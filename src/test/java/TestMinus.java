import de.hbz.lobid.helper.Minus;

import org.metafacture.framework.StreamReceiver;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;

/*
 * Copyright 2021 Fabian Steeg, hbz
 *
 * Licensed under the Apache License, Version 2.0 the "License";
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


/**
 * Tests Metafix field level methods. Following the cheat sheet examples at
 * https://github.com/LibreCat/Catmandu/wiki/Fixes-Cheat-Sheet
 *
 * @author Fabian Steeg
 */
@ExtendWith(MockitoExtension.class) // checkstyle-disable-line JavaNCSS
public class TestMinus {

    @Mock
    private StreamReceiver streamReceiver;

    public TestMinus() {
    }

    @Test
    public void shouldMinusValue() {
        MetafixTestHelpers.assertFix(streamReceiver, Arrays.asList(
                "minus(number, '3')"
            ),
            i -> {
                i.startRecord("1");
                i.literal("number", "1");
                i.endRecord();
            },
            o -> {
                o.get().startRecord("1");
                o.get().literal("number", "-2");
                o.get().endRecord();
            }
        );
    }

    @Test
    public void shouldMinusValueInHash() {
        MetafixTestHelpers.assertFix(streamReceiver, Arrays.asList(
                "minus(number.name, '4')"
            ),
            i -> {
                i.startRecord("1");
                i.startEntity("number");
                i.literal("name", "3");
                i.literal("type", "TEST");
                i.endEntity();
                i.endRecord();
            },
            o -> {
                o.get().startRecord("1");
                o.get().startEntity("number");
                o.get().literal("name", "-1");
                o.get().literal("type", "TEST");
                o.get().endEntity();
                o.get().endRecord();
            }
        );
    }

    @Test
    public void shouldMinusValueInArray() {
        MetafixTestHelpers.assertFix(streamReceiver, Arrays.asList(
                "minus('numbers[].1', '5')"
            ),
            i -> {
                i.startRecord("1");
                i.startEntity("numbers[]");
                i.literal("1", "2");
                i.literal("2", "4");
                i.literal("3", "6");
                i.endEntity();
                i.endRecord();
            },
            o -> {
                o.get().startRecord("1");
                o.get().startEntity("numbers[]");
                o.get().literal("1", "-3");
                o.get().literal("2", "4");
                o.get().literal("3", "6");
                o.get().endEntity();
                o.get().endRecord();
            }
        );
    }

    @Test
    // See issue metafacture-fix#100
    public void shouldMinusValueInEntireArray() {
        MetafixTestHelpers.assertFix(streamReceiver, Arrays.asList(
                "minus('numbers[].*', '6')"
            ),
            i -> {
                i.startRecord("1");
                i.startEntity("numbers[]");
                i.literal("1", "1");
                i.literal("2", "2");
                i.literal("3", "3");
                i.endEntity();
                i.endRecord();
            },
            o -> {
                o.get().startRecord("1");
                o.get().startEntity("numbers[]");
                o.get().literal("1", "-5");
                o.get().literal("2", "-4");
                o.get().literal("3", "-3");
                o.get().endEntity();
                o.get().endRecord();
            }
        );
    }

    @Test
    // See issue #601
    public void shouldMinusValueInNestedArray() {
        MetafixTestHelpers.assertFix(streamReceiver, Arrays.asList(
                "minus('nestedTest[].*.numbers[].*', '7')"
            ),
            i -> {
                i.startRecord("1");
                i.startEntity("nestedTest[]");
                i.startEntity("1");
                i.startEntity("numbers[]");
                i.literal("1", "5");
                i.literal("2", "10");
                i.literal("3", "15");
                i.endEntity();
                i.endEntity();
                i.startEntity("2");
                i.startEntity("numbers[]");
                i.literal("1", "20");
                i.literal("2", "25");
                i.literal("3", "30");
                i.endEntity();
                i.endEntity();
                i.endEntity();
                i.endRecord();
            },
            (o, f) -> {
                o.get().startRecord("1");
                o.get().startEntity("nestedTest[]");
                o.get().startEntity("1");
                o.get().startEntity("numbers[]");
                o.get().literal("1", "-2");
                o.get().literal("2", "3");
                o.get().literal("3", "8");
                f.apply(2).endEntity();
                o.get().startEntity("2");
                o.get().startEntity("numbers[]");
                o.get().literal("1", "13");
                o.get().literal("2", "18");
                o.get().literal("3", "23");
                f.apply(3).endEntity();
                o.get().endRecord();
            }
        );
    }

    @Test
    // See issue #601
    public void shouldMinusValueInArraySubField() {
        MetafixTestHelpers.assertFix(streamReceiver, Arrays.asList(
                "minus('coll[].*.number', '9')"
            ),
            i -> {
                i.startRecord("1");
                i.startEntity("coll[]");
                i.startEntity("1");
                i.literal("number", "1");
                i.literal("b", "Dog");
                i.endEntity();
                i.startEntity("2");
                i.literal("number", "4");
                i.literal("b", "Ape");
                i.endEntity();
                i.startEntity("3");
                i.literal("number", "7");
                i.endEntity();
                i.startEntity("4");
                i.literal("number", "14");
                i.endEntity();
                i.endEntity();
                i.endRecord();
            },
            (o, f) -> {
                o.get().startRecord("1");
                o.get().startEntity("coll[]");
                o.get().startEntity("1");
                o.get().literal("number", "-8");
                o.get().literal("b", "Dog");
                o.get().endEntity();
                o.get().startEntity("2");
                o.get().literal("number", "-5");
                o.get().literal("b", "Ape");
                o.get().endEntity();
                o.get().startEntity("3");
                o.get().literal("number", "-2");
                o.get().endEntity();
                o.get().startEntity("4");
                o.get().literal("number", "5");
                f.apply(2).endEntity();
                o.get().endRecord();
            }
        );
    }

    @Test
    // See issue metafacture-fix#100
    public void shouldNotMinusValueToArray() {
        MetafixTestHelpers.assertExecutionException(IllegalStateException.class, "Expected String, got Array", () ->
            MetafixTestHelpers.assertFix(streamReceiver, Arrays.asList(
                    "minus('numbers[]', '3')"
                ),
                i -> {
                    i.startRecord("1");
                    i.startEntity("numbers[]");
                    i.literal("1", "1");
                    i.literal("2", "2");
                    i.literal("3", "3");
                    i.endEntity();
                    i.endRecord();
                },
                o -> {
                }
            )
        );
    }

    @Test
    public void shouldNotMinusValueToArrayWithWildcard() {
        MetafixTestHelpers.assertExecutionException(IllegalStateException.class, "Expected String, got Array", () ->
            MetafixTestHelpers.assertFix(streamReceiver, Arrays.asList(
                    "minus('number?[]', '4')"
                ),
                i -> {
                    i.startRecord("1");
                    i.startEntity("numbers[]");
                    i.literal("1", "1");
                    i.literal("2", "2");
                    i.literal("3", "3");
                    i.endEntity();
                    i.endRecord();
                },
                o -> {
                }
            )
        );
    }

    @Test
    public void shouldNotMinusValueToArrayWithWildcardNested() {
        MetafixTestHelpers.assertExecutionException(IllegalStateException.class, "Expected String, got Array", () ->
            MetafixTestHelpers.assertFix(streamReceiver, Arrays.asList(
                    "minus('some.number?[]', '3')"
                ),
                i -> {
                    i.startRecord("1");
                    i.startEntity("some");
                    i.startEntity("numbers[]");
                    i.literal("1", "1");
                    i.literal("2", "2");
                    i.literal("3", "3");
                    i.endEntity();
                    i.endEntity();
                    i.endRecord();
                },
                o -> {
                }
            )
        );
    }
}
