package net.nerdorg.minehop.networking.codec;

import java.util.function.Function;

/**
 * 1.20.1 backport shim of the 1.20.5+ {@code net.minecraft.network.codec.StreamCodec} (same Mojang-style names, so the
 * payload records keep their exact 1.21.4 codec definitions: same field order, same wire types). Minecraft 1.20.1 has no
 * stream codecs; every loader's network helper encodes/decodes the raw custom payload bytes with {@link #encode} /
 * {@link #decode}. Loader independent (common code).
 */
public interface StreamCodec<B, V> {
    V decode(B buf);

    void encode(B buf, V value);

    @FunctionalInterface
    interface StreamMemberEncoder<B, V> {
        void encode(V value, B buf);
    }

    @FunctionalInterface
    interface StreamDecoder<B, V> {
        V decode(B buf);
    }

    static <B, V> StreamCodec<B, V> ofMember(StreamMemberEncoder<B, V> encoder, StreamDecoder<B, V> decoder) {
        return new StreamCodec<>() {
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

    static <B, C, T1> StreamCodec<B, C> composite(
            StreamCodec<? super B, T1> c1, Function<C, T1> f1,
            Function<T1, C> to) {
        return new StreamCodec<>() {
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

    static <B, C, T1, T2> StreamCodec<B, C> composite(
            StreamCodec<? super B, T1> c1, Function<C, T1> f1,
            StreamCodec<? super B, T2> c2, Function<C, T2> f2,
            Function2<T1, T2, C> to) {
        return new StreamCodec<>() {
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

    static <B, C, T1, T2, T3> StreamCodec<B, C> composite(
            StreamCodec<? super B, T1> c1, Function<C, T1> f1,
            StreamCodec<? super B, T2> c2, Function<C, T2> f2,
            StreamCodec<? super B, T3> c3, Function<C, T3> f3,
            Function3<T1, T2, T3, C> to) {
        return new StreamCodec<>() {
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

    static <B, C, T1, T2, T3, T4> StreamCodec<B, C> composite(
            StreamCodec<? super B, T1> c1, Function<C, T1> f1,
            StreamCodec<? super B, T2> c2, Function<C, T2> f2,
            StreamCodec<? super B, T3> c3, Function<C, T3> f3,
            StreamCodec<? super B, T4> c4, Function<C, T4> f4,
            Function4<T1, T2, T3, T4, C> to) {
        return new StreamCodec<>() {
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

    static <B, C, T1, T2, T3, T4, T5> StreamCodec<B, C> composite(
            StreamCodec<? super B, T1> c1, Function<C, T1> f1,
            StreamCodec<? super B, T2> c2, Function<C, T2> f2,
            StreamCodec<? super B, T3> c3, Function<C, T3> f3,
            StreamCodec<? super B, T4> c4, Function<C, T4> f4,
            StreamCodec<? super B, T5> c5, Function<C, T5> f5,
            Function5<T1, T2, T3, T4, T5, C> to) {
        return new StreamCodec<>() {
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

    static <B, C, T1, T2, T3, T4, T5, T6> StreamCodec<B, C> composite(
            StreamCodec<? super B, T1> c1, Function<C, T1> f1,
            StreamCodec<? super B, T2> c2, Function<C, T2> f2,
            StreamCodec<? super B, T3> c3, Function<C, T3> f3,
            StreamCodec<? super B, T4> c4, Function<C, T4> f4,
            StreamCodec<? super B, T5> c5, Function<C, T5> f5,
            StreamCodec<? super B, T6> c6, Function<C, T6> f6,
            Function6<T1, T2, T3, T4, T5, T6, C> to) {
        return new StreamCodec<>() {
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

    static <B, C, T1, T2, T3, T4, T5, T6, T7> StreamCodec<B, C> composite(
            StreamCodec<? super B, T1> c1, Function<C, T1> f1,
            StreamCodec<? super B, T2> c2, Function<C, T2> f2,
            StreamCodec<? super B, T3> c3, Function<C, T3> f3,
            StreamCodec<? super B, T4> c4, Function<C, T4> f4,
            StreamCodec<? super B, T5> c5, Function<C, T5> f5,
            StreamCodec<? super B, T6> c6, Function<C, T6> f6,
            StreamCodec<? super B, T7> c7, Function<C, T7> f7,
            Function7<T1, T2, T3, T4, T5, T6, T7, C> to) {
        return new StreamCodec<>() {
            @Override
            public C decode(B buf) {
                T1 t1 = c1.decode(buf);
                T2 t2 = c2.decode(buf);
                T3 t3 = c3.decode(buf);
                T4 t4 = c4.decode(buf);
                T5 t5 = c5.decode(buf);
                T6 t6 = c6.decode(buf);
                T7 t7 = c7.decode(buf);
                return to.apply(t1, t2, t3, t4, t5, t6, t7);
            }

            @Override
            public void encode(B buf, C value) {
                c1.encode(buf, f1.apply(value));
                c2.encode(buf, f2.apply(value));
                c3.encode(buf, f3.apply(value));
                c4.encode(buf, f4.apply(value));
                c5.encode(buf, f5.apply(value));
                c6.encode(buf, f6.apply(value));
                c7.encode(buf, f7.apply(value));
            }
        };
    }

    static <B, C, T1, T2, T3, T4, T5, T6, T7, T8> StreamCodec<B, C> composite(
            StreamCodec<? super B, T1> c1, Function<C, T1> f1,
            StreamCodec<? super B, T2> c2, Function<C, T2> f2,
            StreamCodec<? super B, T3> c3, Function<C, T3> f3,
            StreamCodec<? super B, T4> c4, Function<C, T4> f4,
            StreamCodec<? super B, T5> c5, Function<C, T5> f5,
            StreamCodec<? super B, T6> c6, Function<C, T6> f6,
            StreamCodec<? super B, T7> c7, Function<C, T7> f7,
            StreamCodec<? super B, T8> c8, Function<C, T8> f8,
            Function8<T1, T2, T3, T4, T5, T6, T7, T8, C> to) {
        return new StreamCodec<>() {
            @Override
            public C decode(B buf) {
                T1 t1 = c1.decode(buf);
                T2 t2 = c2.decode(buf);
                T3 t3 = c3.decode(buf);
                T4 t4 = c4.decode(buf);
                T5 t5 = c5.decode(buf);
                T6 t6 = c6.decode(buf);
                T7 t7 = c7.decode(buf);
                T8 t8 = c8.decode(buf);
                return to.apply(t1, t2, t3, t4, t5, t6, t7, t8);
            }

            @Override
            public void encode(B buf, C value) {
                c1.encode(buf, f1.apply(value));
                c2.encode(buf, f2.apply(value));
                c3.encode(buf, f3.apply(value));
                c4.encode(buf, f4.apply(value));
                c5.encode(buf, f5.apply(value));
                c6.encode(buf, f6.apply(value));
                c7.encode(buf, f7.apply(value));
                c8.encode(buf, f8.apply(value));
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

    @FunctionalInterface
    interface Function7<A, B, C, D, E, F, G, R> {
        R apply(A a, B b, C c, D d, E e, F f, G g);
    }

    @FunctionalInterface
    interface Function8<A, B, C, D, E, F, G, H, R> {
        R apply(A a, B b, C c, D d, E e, F f, G g, H h);
    }
}
