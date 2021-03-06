/*
 * Copyright (c) 2017 Pantheon Technologies s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.mdsal.binding.javav2.dom.codec.impl;

import com.google.common.annotations.Beta;
import com.google.common.collect.ImmutableSet;
import java.lang.reflect.Method;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.Callable;
import org.opendaylight.mdsal.binding.javav2.dom.codec.impl.context.UnionValueOptionContext;
import org.opendaylight.mdsal.binding.javav2.dom.codec.impl.context.base.BindingCodecContext;
import org.opendaylight.mdsal.binding.javav2.dom.codec.impl.value.ReflectionBasedCodec;
import org.opendaylight.mdsal.binding.javav2.generator.util.JavaIdentifier;
import org.opendaylight.mdsal.binding.javav2.generator.util.JavaIdentifierNormalizer;
import org.opendaylight.yangtools.concepts.Codec;
import org.opendaylight.yangtools.yang.model.api.TypeDefinition;
import org.opendaylight.yangtools.yang.model.api.type.UnionTypeDefinition;

/**
 * Codec for serialize/deserialize union type.
 *
 */
@Beta
public final class UnionTypeCodec extends ReflectionBasedCodec {

    private final ImmutableSet<UnionValueOptionContext> typeCodecs;

    private UnionTypeCodec(final Class<?> unionCls, final Set<UnionValueOptionContext> codecs) {
        super(unionCls);
        typeCodecs = ImmutableSet.copyOf(codecs);
    }

    /**
     * Loading union type codec for all subtypes of union.
     *
     * @param unionCls
     *            - binding class of union
     * @param unionType
     *            - type definition of union
     * @param bindingCodecContext
     *            - binding codec context
     * @return union codec
     */
    public static Callable<UnionTypeCodec> loader(final Class<?> unionCls, final UnionTypeDefinition unionType,
            final BindingCodecContext bindingCodecContext) {
        return () -> {
            final Set<UnionValueOptionContext> values = new LinkedHashSet<>();
            for (final TypeDefinition<?> subtype : unionType.getTypes()) {
                final Method valueGetter = unionCls.getMethod("get" + JavaIdentifierNormalizer
                        .normalizeSpecificIdentifier(subtype.getQName().getLocalName(), JavaIdentifier.CLASS));
                final Class<?> valueType = valueGetter.getReturnType();
                final Codec<Object, Object> valueCodec = bindingCodecContext.getCodec(valueType, subtype);
                values.add(new UnionValueOptionContext(unionCls, valueType, valueGetter, valueCodec));
            }
            return new UnionTypeCodec(unionCls, values);
        };
    }

    @Override
    public Object deserialize(final Object input) {
        for (final UnionValueOptionContext member : typeCodecs) {
            final Object ret = member.deserializeUnion(input);
            if (ret != null) {
                return ret;
            }
        }

        throw new IllegalArgumentException(
                String.format("Failed to construct instance of %s for input %s", getTypeClass(), input));
    }

    @Override
    public Object serialize(final Object input) {
        if (input != null) {
            for (final UnionValueOptionContext valCtx : typeCodecs) {
                final Object domValue = valCtx.serialize(input);
                if (domValue != null) {
                    return domValue;
                }
            }
        }
        return null;
    }
}
