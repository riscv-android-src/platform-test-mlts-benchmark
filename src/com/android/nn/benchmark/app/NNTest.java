/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.nn.benchmark.app;

import android.test.suitebuilder.annotation.LargeTest;
import android.test.suitebuilder.annotation.MediumTest;

import com.android.nn.benchmark.core.TestModels;

import org.junit.Test;

/**
 * NNAPI benchmark test.
 * To run the test, please use command
 *
 * adb shell am instrument -w
 * com.android.nn.benchmark.app/androidx.test.runner.AndroidJUnitRunner
 *
 * To run only one model, please run:
 * adb shell am instrument
 * -e class "com.android.nn.benchmark.app.NNTest#testNNAPI[MODEL_NAME]"
 * -w com.android.nn.benchmark.app/androidx.test.runner.AndroidJUnitRunner
 *
 */
public class NNTest extends BenchmarkTestBase {

    public NNTest(TestModels.TestModelEntry model) {
        super(model, /*acceleratorName=*/null);
    }

    @Test
    @MediumTest
    public void testNNAPI() {
        TestAction ta = new TestAction(mModel, WARMUP_SHORT_SECONDS, RUNTIME_SHORT_SECONDS);
        runTest(ta, mModel.getTestName());
    }

    @Test
    @LargeTest
    public void testNNAPI10Seconds() {
        TestAction ta = new TestAction(mModel, WARMUP_REPEATABLE_SECONDS,
                RUNTIME_REPEATABLE_SECONDS);
        runTest(ta, mModel.getTestName());
    }

    @Test
    @LargeTest
    public void testNNAPIAllData() {
        setCompleteInputSet(true);
        TestAction ta = new TestAction(mModel, WARMUP_REPEATABLE_SECONDS,
                COMPLETE_SET_TIMEOUT_SECOND);
        runTest(ta, mModel.getTestName());
    }
}
