/* Copyright 2023 hbz, Jens Wille. Licensed under the EPL 2.0 */

package de.hbz.lobid.helper;

import org.metafacture.metafix.Metafix;
import org.metafacture.metafix.Record;
import org.metafacture.metafix.Value;
import org.metafacture.metafix.api.FixFunction;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;


public class DropLocal implements FixFunction {

    public DropLocal() {
    }

    @Override
    public void apply(final Metafix aMetafix, final Record aRecord, final List<String> aParams, final Map<String, String> aOptions) {
        final String memberCode = aMetafix.getVars().get("member");
        final Set<String> localFields = new HashSet<>();

        final BiConsumer<Runnable, Value.TypeMatcher> consumer = (r, m) -> m
            .ifHash(h -> {
                final Value localField = h.get("9");
                final Value memberField = h.get("M");

                final boolean isLocal = localField != null && localField.<Boolean>extractType((n, c) -> n
                    .ifString(s -> c.accept("LOCAL".equals(s)))
                    .orElse(v -> c.accept(false)));

                if (isLocal && memberField != null && !memberCode.equals(memberField.asString())) {
                    r.run();
                }
            });

        aRecord.forEach((f, v) -> consumer.accept(() -> localFields.add(f), v.matchType()
            .ifArray(a -> {
                for (int i = a.size() - 1; i >= 0; --i) {
                    final int index = i;
                    consumer.accept(() -> a.remove(index), a.get(index).matchType());
                }
            })));

        localFields.forEach(aRecord::removeField);
    }

}
