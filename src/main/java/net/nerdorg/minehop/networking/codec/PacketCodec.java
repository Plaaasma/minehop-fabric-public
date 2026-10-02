package net.nerdorg.minehop.networking.codec;

import java.util.function.Function;

/**
 * 1.20.1 backport shim of the 1.20.5+ {@code net.minecraft.network.codec.PacketCodec}: Minecraft
 * 1.20.1 has no stream codecs, so the payload records keep their exact 1.21.4 codec definitions
 * (same field order, same wire types) on top of this minimal re-implementation. Fabric API's
 * {@code FabricPacket#write} / {@code PacketType} reader delegate to {@link #encode} / {@link #decode}.
 */
public interface PacketCodec<B, V> {
    V decode(B buf);

    void encode(B buf, V value);

    @FunctionalInterface
    interface ValueFirstEncoder<B, V> {
        void encode(V value, B buf);
    }

    @FunctionalInterface
    interface PacketDecoder<B, V> {
        V decode(B buf);
    }

    static <B, V> PacketCodec<B, V> of(ValueFirstEncoder<B, V> encoder, PacketDecoder<B, V> decoder) {
        return new PacketCodec<>() {
            @Override
            public V decode(B buf) {
                return decoder.decode(buf);
            }

            @Override
            public void encode(B buf, V value) {
                encoder.encode(value, buf);
            }
        };
    }

    static <B, C, T1> PacketCodec<B, C> tuple(
            PacketCodec<? super B, T1> c1, Function<C, T1> f1,
            Function<T1, C> to) {
        return new PacketCodec<>() {
            @Override
            public C decode(B buf) {
                T1 t1 = c1.decode(buf);
                return to.apply(t1);
            }

            @Override
            public void encode(B buf, C value) {
                c1.encode(buf, f1.apply(value));
            }
        };
    }

    static <B, C, T1, T2> PacketCodec<B, C> tuple(
            PacketCodec<? super B, T1> c1, Function<C, T1> f1,
            PacketCodec<? super B, T2> c2, Function<C, T2> f2,
            Function2<T1, T2, C> to) {
        return new PacketCodec<>() {
            @Override
            public C decode(B buf) {
                T1 t1 = c1.decode(buf);
                T2 t2 = c2.decode(buf);
                return to.apply(t1, t2);
            }

            @Override
            public void encode(B buf, C value) {
                c1.encode(buf, f1.apply(value));
                c2.encode(buf, f2.apply(value));
            }
        };
    }

    static <B, C, T1, T2, T3> PacketCodec<B, C> tuple(
            PacketCodec<? super B, T1> c1, Function<C, T1> f1,
            PacketCodec<? super B, T2> c2, Function<C, T2> f2,
            PacketCodec<? super B, T3> c3, Function<C, T3> f3,
            Function3<T1, T2, T3, C> to) {
        return new PacketCodec<>() {
            @Override
            public C decode(B buf) {
                T1 t1 = c1.decode(buf);
                T2 t2 = c2.decode(buf);
                T3 t3 = c3.decode(buf);
                return to.apply(t1, t2, t3);
            }

            @Override
            public void encode(B buf, C value) {
                c1.encode(buf, f1.apply(value));
                c2.encode(buf, f2.apply(value));
                c3.encode(buf, f3.apply(value));
            }
        };
    }

    static <B, C, T1, T2, T3, T4> PacketCodec<B, C> tuple(
            PacketCodec<? super B, T1> c1, Function<C, T1> f1,
            PacketCodec<? super B, T2> c2, Function<C, T2> f2,
            PacketCodec<? super B, T3> c3, Function<C, T3> f3,
            PacketCodec<? super B, T4> c4, Function<C, T4> f4,
            Function4<T1, T2, T3, T4, C> to) {
        return new PacketCodec<>() {
            @Override
            public C decode(B buf) {
                T1 t1 = c1.decode(buf);
                T2 t2 = c2.decode(buf);
                T3 t3 = c3.decode(buf);
                T4 t4 = c4.decode(buf);
                return to.apply(t1, t2, t3, t4);
            }

            @Override
            public void encode(B buf, C value) {
                c1.encode(buf, f1.apply(value));
                c2.encode(buf, f2.apply(value));
                c3.encode(buf, f3.apply(value));
                c4.encode(buf, f4.apply(value));
            }
        };
    }

    static <B, C, T1, T2, T3, T4, T5> PacketCodec<B, C> tuple(
            PacketCodec<? super B, T1> c1, Function<C, T1> f1,
            PacketCodec<? super B, T2> c2, Function<C, T2> f2,
            PacketCodec<? super B, T3> c3, Function<C, T3> f3,
            PacketCodec<? super B, T4> c4, Function<C, T4> f4,
            PacketCodec<? super B, T5> c5, Function<C, T5> f5,
            Function5<T1, T2, T3, T4, T5, C> to) {
        return new PacketCodec<>() {
            @Override
            public C decode(B buf) {
                T1 t1 = c1.decode(buf);
                T2 t2 = c2.decode(buf);
                T3 t3 = c3.decode(buf);
                T4 t4 = c4.decode(buf);
                T5 t5 = c5.decode(buf);
                return to.apply(t1, t2, t3, t4, t5);
            }

            @Override
            public void encode(B buf, C value) {
                c1.encode(buf, f1.apply(value));
                c2.encode(buf, f2.apply(value));
                c3.encode(buf, f3.apply(value));
                c4.encode(buf, f4.apply(value));
                c5.encode(buf, f5.apply(value));
            }
        };
    }

    static <B, C, T1, T2, T3, T4, T5, T6> PacketCodec<B, C> tuple(
            PacketCodec<? super B, T1> c1, Function<C, T1> f1,
            PacketCodec<? super B, T2> c2, Function<C, T2> f2,
            PacketCodec<? super B, T3> c3, Function<C, T3> f3,
            PacketCodec<? super B, T4> c4, Function<C, T4> f4,
            PacketCodec<? super B, T5> c5, Function<C, T5> f5,
            PacketCodec<? super B, T6> c6, Function<C, T6> f6,
            Function6<T1, T2, T3, T4, T5, T6, C> to) {
        return new PacketCodec<>() {
            @Override
            public C decode(B buf) {
                T1 t1 = c1.decode(buf);
                T2 t2 = c2.decode(buf);
                T3 t3 = c3.decode(buf);
                T4 t4 = c4.decode(buf);
                T5 t5 = c5.decode(buf);
                T6 t6 = c6.decode(buf);
                return to.apply(t1, t2, t3, t4, t5, t6);
            }

            @Override
            public void encode(B buf, C value) {
                c1.encode(buf, f1.apply(value));
                c2.encode(buf, f2.apply(value));
                c3.encode(buf, f3.apply(value));
                c4.encode(buf, f4.apply(value));
                c5.encode(buf, f5.apply(value));
                c6.encode(buf, f6.apply(value));
            }
        };
    }

    @FunctionalInterface
    interface Function2<A, B, R> {
        R apply(A a, B b);
    }

    @FunctionalInterface
    interface Function3<A, B, C, R> {
        R apply(A a, B b, C c);
    }

    @FunctionalInterface
    interface Function4<A, B, C, D, R> {
        R apply(A a, B b, C c, D d);
    }

    @FunctionalInterface
    interface Function5<A, B, C, D, E, R> {
        R apply(A a, B b, C c, D d, E e);
    }

    @FunctionalInterface
    interface Function6<A, B, C, D, E, F, R> {
        R apply(A a, B b, C c, D d, E e, F f);
    }
}
