import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Rewrites every java.lang.invoke.LambdaMetafactory.metafactory call site into a plain generated class,
 * so the result needs no invokedynamic support from the dex tool or the Android runtime.
 *
 * usage: Desugar <input-classes-dir> <output-classes-dir>
 */
public class Desugar {
    static final String LMF = "java/lang/invoke/LambdaMetafactory";
    static int sites = 0, classes = 0;

    public static void main(String[] args) throws Exception {
        Path in = Paths.get(args[0]).toAbsolutePath(), out = Paths.get(args[1]).toAbsolutePath();
        List<Path> files = new ArrayList<Path>();
        try (Stream<Path> s = Files.walk(in)) {
            s.filter(p -> Files.isRegularFile(p)).forEach(files::add);
        }
        for (Path p : files) {
            Path target = out.resolve(in.relativize(p).toString());
            Files.createDirectories(target.getParent());
            if (!p.toString().endsWith(".class")) {
                Files.copy(p, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                continue;
            }
            Files.write(target, transform(Files.readAllBytes(p), out));
            classes++;
        }
        System.out.println("desugared " + sites + " lambda call sites in " + classes + " classes");
    }

    static String liftedName(String owner, String name) {
        return name + "$" + owner.replace('/', '_').replace('$', '_');
    }

    static byte[] transform(byte[] bytes, final Path outRoot) throws IOException {
        final ClassReader cr = new ClassReader(bytes);
        final String owner = cr.getClassName();
        final boolean isIface = (cr.getAccess() & Opcodes.ACC_INTERFACE) != 0;

        // Pass 1: which of this class's own methods are lambda targets?
        final Set<String> targets = new HashSet<String>();
        cr.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(int a, String n, String d, String s, String[] e) {
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitInvokeDynamicInsn(String nm, String ds, Handle bsm, Object... bargs) {
                        if (isLambda(bsm)) {
                            Handle h = (Handle) bargs[1];
                            if (h.getOwner().equals(owner)) targets.add(h.getName() + h.getDesc());
                        }
                    }
                };
            }
        }, ClassReader.SKIP_FRAMES | ClassReader.SKIP_DEBUG);

        // Pass 2: rewrite.
        final ClassWriter cw = new ClassWriter(0);
        final int[] counter = {0};
        cr.accept(new ClassVisitor(Opcodes.ASM9, cw) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String desc, String sig, String[] exc) {
                if (targets.contains(name + desc)) {
                    if ((access & Opcodes.ACC_PRIVATE) != 0) {
                        access = (access & ~Opcodes.ACC_PRIVATE) | (isIface ? Opcodes.ACC_PUBLIC : 0);
                    }
                    if (name.startsWith("lambda$")) name = liftedName(owner, name);
                }
                MethodVisitor mv = super.visitMethod(access, name, desc, sig, exc);
                return new MethodVisitor(Opcodes.ASM9, mv) {
                    @Override
                    public void visitMethodInsn(int opcode, String o, String n, String d, boolean itf) {
                        // A private method that became package-private is no longer a "direct" method,
                        // so existing invokespecial calls to it must become virtual calls.
                        if (opcode == Opcodes.INVOKESPECIAL && o.equals(owner) && !n.equals("<init>")
                                && targets.contains(n + d)) {
                            String nn = n.startsWith("lambda$") ? liftedName(owner, n) : n;
                            super.visitMethodInsn(isIface ? Opcodes.INVOKEINTERFACE : Opcodes.INVOKEVIRTUAL, o, nn, d, isIface);
                            return;
                        }
                        super.visitMethodInsn(opcode, o, n, d, itf);
                    }

                    @Override
                    public void visitInvokeDynamicInsn(String nm, String ds, Handle bsm, Object... bargs) {
                        if (!isLambda(bsm)) {
                            super.visitInvokeDynamicInsn(nm, ds, bsm, bargs);
                            return;
                        }
                        String lname = owner + "$$Lambda$" + (++counter[0]);
                        byte[] gen = generate(lname, owner, nm, ds, bargs);
                        try {
                            Path f = outRoot.resolve(lname + ".class");
                            Files.createDirectories(f.getParent());
                            Files.write(f, gen);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                        sites++;
                        super.visitMethodInsn(Opcodes.INVOKESTATIC, lname, "create", ds, false);
                    }
                };
            }
        }, 0);
        return cw.toByteArray();
    }

    static boolean isLambda(Handle bsm) {
        if (!bsm.getOwner().equals(LMF)) return false;
        if (!bsm.getName().equals("metafactory")) {
            throw new IllegalStateException("unsupported lambda factory: " + bsm.getName());
        }
        return true;
    }

    /** Builds one lambda class: fields for captured values, create(...) factory, and the SAM method. */
    static byte[] generate(String lname, String outer, String samName, String indyDesc, Object[] bargs) {
        Type indy = Type.getMethodType(indyDesc);
        final Type[] captured = indy.getArgumentTypes();
        Type iface = indy.getReturnType();
        Type samT = (Type) bargs[0];
        Handle impl = (Handle) bargs[1];

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cw.visit(Opcodes.V1_8, Opcodes.ACC_FINAL | Opcodes.ACC_SYNTHETIC | Opcodes.ACC_SUPER, lname, null,
                "java/lang/Object", new String[]{iface.getInternalName()});
        Type self = Type.getObjectType(lname);

        for (int i = 0; i < captured.length; i++) {
            cw.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL, "f" + i, captured[i].getDescriptor(), null, null)
                    .visitEnd();
        }

        // constructor
        String ctorDesc = Type.getMethodDescriptor(Type.VOID_TYPE, captured);
        GeneratorAdapter g = new GeneratorAdapter(cw.visitMethod(0, "<init>", ctorDesc, null, null), 0, "<init>", ctorDesc);
        g.visitCode();
        g.loadThis();
        g.invokeConstructor(Type.getObjectType("java/lang/Object"), new Method("<init>", "()V"));
        for (int i = 0; i < captured.length; i++) {
            g.loadThis();
            g.loadArg(i);
            g.putField(self, "f" + i, captured[i]);
        }
        g.returnValue();
        g.endMethod();

        // static factory
        g = new GeneratorAdapter(cw.visitMethod(Opcodes.ACC_STATIC, "create", indyDesc, null, null),
                Opcodes.ACC_STATIC, "create", indyDesc);
        g.visitCode();
        g.newInstance(self);
        g.dup();
        for (int i = 0; i < captured.length; i++) g.loadArg(i);
        g.invokeConstructor(self, new Method("<init>", ctorDesc));
        g.returnValue();
        g.endMethod();

        // the functional interface method
        String samDesc = samT.getDescriptor();
        g = new GeneratorAdapter(cw.visitMethod(Opcodes.ACC_PUBLIC, samName, samDesc, null, null),
                Opcodes.ACC_PUBLIC, samName, samDesc);
        g.visitCode();

        int tag = impl.getTag();
        Type[] implArgs = Type.getArgumentTypes(impl.getDesc());
        Type implRet = Type.getReturnType(impl.getDesc());
        Type ownerT = Type.getObjectType(impl.getOwner());

        List<Type> dst = new ArrayList<Type>();
        if (tag != Opcodes.H_INVOKESTATIC && tag != Opcodes.H_NEWINVOKESPECIAL) dst.add(ownerT);
        for (Type t : implArgs) dst.add(t);

        // sources: captured fields, then the SAM parameters
        List<Type> srcTypes = new ArrayList<Type>();
        for (Type t : captured) srcTypes.add(t);
        Type[] samArgs = samT.getArgumentTypes();
        for (Type t : samArgs) srcTypes.add(t);
        if (srcTypes.size() != dst.size()) {
            throw new IllegalStateException("argument count mismatch in " + lname + ": " + srcTypes + " vs " + dst);
        }

        if (tag == Opcodes.H_NEWINVOKESPECIAL) {
            g.newInstance(ownerT);
            g.dup();
        }
        for (int i = 0; i < srcTypes.size(); i++) {
            if (i < captured.length) {
                g.loadThis();
                g.getField(self, "f" + i, captured[i]);
            } else {
                g.loadArg(i - captured.length);
            }
            adapt(g, srcTypes.get(i), dst.get(i));
        }

        String implName = impl.getName();
        if (impl.getOwner().equals(outer) && implName.startsWith("lambda$")) implName = liftedName(outer, implName);
        boolean itf = impl.isInterface();
        Type resultT = implRet;
        switch (tag) {
            case Opcodes.H_INVOKESTATIC:
                g.visitMethodInsn(Opcodes.INVOKESTATIC, impl.getOwner(), implName, impl.getDesc(), itf);
                break;
            case Opcodes.H_INVOKEVIRTUAL:
                g.visitMethodInsn(Opcodes.INVOKEVIRTUAL, impl.getOwner(), implName, impl.getDesc(), false);
                break;
            case Opcodes.H_INVOKEINTERFACE:
                g.visitMethodInsn(Opcodes.INVOKEINTERFACE, impl.getOwner(), implName, impl.getDesc(), true);
                break;
            case Opcodes.H_INVOKESPECIAL:
                // private instance targets were lifted to package-private, so a virtual call reaches them.
                g.visitMethodInsn(itf ? Opcodes.INVOKEINTERFACE : Opcodes.INVOKEVIRTUAL,
                        impl.getOwner(), implName, impl.getDesc(), itf);
                break;
            case Opcodes.H_NEWINVOKESPECIAL:
                g.visitMethodInsn(Opcodes.INVOKESPECIAL, impl.getOwner(), "<init>", impl.getDesc(), false);
                resultT = ownerT;
                break;
            default:
                throw new IllegalStateException("unsupported handle kind " + tag + " in " + lname);
        }

        Type samRet = samT.getReturnType();
        if (samRet.getSort() == Type.VOID) {
            if (resultT.getSort() != Type.VOID) {
                if (resultT.getSize() == 2) g.pop2(); else g.pop();
            }
        } else {
            adapt(g, resultT, samRet);
        }
        g.returnValue();
        g.endMethod();

        cw.visitEnd();
        return cw.toByteArray();
    }

    static boolean isPrim(Type t) {
        return t.getSort() >= Type.BOOLEAN && t.getSort() <= Type.DOUBLE;
    }

    static void adapt(GeneratorAdapter g, Type from, Type to) {
        if (from.equals(to)) return;
        boolean fp = isPrim(from), tp = isPrim(to);
        if (!fp && !tp) {
            if (!to.getDescriptor().equals("Ljava/lang/Object;")) g.checkCast(to);
        } else if (fp && !tp) {
            g.box(from);
        } else if (!fp && tp) {
            g.unbox(to);
        } else {
            g.cast(from, to);
        }
    }
}
