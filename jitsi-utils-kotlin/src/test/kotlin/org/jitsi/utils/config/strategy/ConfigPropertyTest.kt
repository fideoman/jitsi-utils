/*
 * Copyright @ 2018 - present 8x8, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jitsi.utils.config.strategy

import io.kotlintest.IsolationMode
import io.kotlintest.shouldBe
import io.kotlintest.shouldThrow
import io.kotlintest.specs.ShouldSpec
import org.jitsi.utils.config.FallbackProperty
import org.jitsi.utils.config.SimpleProperty
import org.jitsi.utils.config.helpers.attributes
import org.jitsi.utils.config.testutils.TestConfigSource
import java.time.Duration

class ConfigPropertyTest : ShouldSpec() {
    override fun isolationMode(): IsolationMode? = IsolationMode.InstancePerLeaf

    val newConfig = TestConfigSource("newConfig", mapOf(
        "newPropInt" to 42,
        "newPropLong" to 43L,
        "onlyNewProp" to Duration.ofSeconds(10)
    ))
    val legacyConfig = TestConfigSource("legacyConfig", mapOf(
        "oldPropInt" to 41,
        "oldPropLong" to 44L,
        "onlyOldProp" to 10
    ))

    init {
        "Simple, read-once property" {
            val property = object : SimpleProperty<Int>(
                attributes {
                    name("newPropInt")
                    readOnce()
                    fromConfig(newConfig)
                }
            ) {}
            should("read the correct value") {
                property.value shouldBe 42
            }
            should("only query the value once") {
                repeat (5) { property.value }
                newConfig.numGetsCalled shouldBe 1
            }
        }
        "Simple, read-every-time property" {
            val property = object : SimpleProperty<Int>(
                attributes {
                    name("newPropInt")
                    readEveryTime()
                    fromConfig(newConfig)
                }
            ) {}
            should("read the correct value") {
                property.value shouldBe 42
            }
            should("read new values if they're changed") {
                newConfig.props["newPropInt"] = 43
                property.value shouldBe 43
            }
            should("query the value every time") {
                repeat (5) { property.value }
                newConfig.numGetsCalled shouldBe 5
            }
        }
        "Converting, read-once property" {
            var numTimesConverterCalled = 0
            val property = object : SimpleProperty<Long>(
                attributes {
                    name("onlyNewProp")
                    readOnce()
                    fromConfig(newConfig)
                    retrievedAs<Duration>() convertedBy { numTimesConverterCalled++; it.toMillis() }
                }
            ) {}
            should("read the correct value") {
                property.value shouldBe 10000
            }
            should("only query the value and converter once") {
                repeat (5) { property.value }
                newConfig.numGetsCalled shouldBe 1
                numTimesConverterCalled shouldBe 1
            }
        }
        "Converting, read-every-time property" {
            var numTimesConverterCalled = 0
            val property = object : SimpleProperty<Long>(
                attributes {
                    name("onlyNewProp")
                    readEveryTime()
                    fromConfig(newConfig)
                    retrievedAs<Duration>() convertedBy { numTimesConverterCalled++; it.toMillis() }
                }
            ) {}
            should("read the correct value") {
                property.value shouldBe 10000
            }
            should("query the value and converter every time") {
                repeat (5) { property.value }
                newConfig.numGetsCalled shouldBe 5
                numTimesConverterCalled shouldBe 5
            }
        }
        "Transforming, read-once property" {
            var numTimesTransformerCalled = 0
            val property = object : SimpleProperty<Int>(
                attributes {
                    name("newPropInt")
                    readOnce()
                    fromConfig(newConfig)
                    transformedBy { numTimesTransformerCalled++; 42 }
                }
            ) {}
            should("read the correct value") {
                property.value shouldBe 42
            }
            should("only query the value and converter once") {
                repeat (5) { property.value }
                newConfig.numGetsCalled shouldBe 1
                numTimesTransformerCalled shouldBe 1
            }
        }
        "Transforming, read-every-time property" {
            var numTimesTransformerCalled = 0
            val property = object : SimpleProperty<Int>(
                attributes {
                    name("newPropInt")
                    readEveryTime()
                    fromConfig(newConfig)
                    transformedBy { numTimesTransformerCalled++; 42 }
                }
            ) {}
            should("read the correct value") {
                property.value shouldBe 42
            }
            should("query the value and converter every time") {
                repeat (5) { property.value }
                newConfig.numGetsCalled shouldBe 5
                numTimesTransformerCalled shouldBe 5
            }
        }
        "Using both retrievedAs and transformedBy" {
            shouldThrow<IllegalStateException> {
                attributes<Long> {
                    name("newPropLong")
                    readEveryTime()
                    fromConfig(newConfig)
                    transformedBy { it }
                    retrievedAs<Duration>() convertedBy { it.toMillis() }
                }
            }
        }
        "Multi, read-once property" {
            val property = object : FallbackProperty<Long>(
                attributes {
                    name("oldPropLong")
                    readOnce()
                    fromConfig(legacyConfig)
                },
                attributes {
                    name("newPropLong")
                    readOnce()
                    fromConfig(newConfig)
                }
            ) {}
            should("read the correct value") {
                property.value shouldBe 44
            }
            should("only query the value once and stop") {
                repeat (5) { property.value }
                legacyConfig.numGetsCalled shouldBe 1
                newConfig.numGetsCalled shouldBe 1
            }
        }
        "Multi, read-every-time property" {
            val property = object : FallbackProperty<Long>(
                attributes {
                    name("oldPropLong")
                    readEveryTime()
                    fromConfig(legacyConfig)
                },
                attributes {
                    name("newPropLong")
                    readEveryTime()
                    fromConfig(newConfig)
                }
            ) {}
            should("read the correct value") {
                property.value shouldBe 44
            }
            should("reflect changes") {
                legacyConfig.props.remove("oldPropLong")
                property.value shouldBe 43
            }
            should("query the value every time") {
                repeat (5) { property.value }
                legacyConfig.numGetsCalled shouldBe 5
                newConfig.numGetsCalled shouldBe 0
            }
        }
    }
}
