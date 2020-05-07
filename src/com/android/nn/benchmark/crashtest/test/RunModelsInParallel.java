/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.nn.benchmark.crashtest.test;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.android.nn.benchmark.core.Processor;
import com.android.nn.benchmark.crashtest.CrashTest;
import com.android.nn.benchmark.crashtest.CrashTestCoordinator.CrashTestIntentInitializer;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class RunModelsInParallel implements CrashTest {

    private static final String MODELS = "models";
    private static final String DURATION = "duration";
    private static final String THREADS = "thread_counts";
    private static final String TEST_NAME = "test_name";

    static public CrashTestIntentInitializer intentInitializer(int[] models, int threadCount,
            Duration duration, String testName) {
        return intent -> {
            intent.putExtra(MODELS, models);
            intent.putExtra(DURATION, duration.toMillis());
            intent.putExtra(THREADS, threadCount);
            intent.putExtra(TEST_NAME, testName);
        };
    }

    private long mTestDurationMillis = 0;
    private int mThreadCount = 0;
    private int[] mTestList = new int[0];
    private String mTestName;
    private Context mContext;

    private ExecutorService mExecutorService = null;
    private final Set<Processor> activeTests = new HashSet<>();
    private CountDownLatch mParallelTestComplete;
    private final List<Boolean> mTestCompletionResults = Collections.synchronizedList(
            new ArrayList<>());
    private ProgressListener mProgressListener;

    @Override
    public void init(Context context, Intent configParams,
            Optional<ProgressListener> progressListener) {
        mTestList = configParams.getIntArrayExtra(MODELS);
        mThreadCount = configParams.getIntExtra(THREADS, 10);
        mTestDurationMillis = configParams.getLongExtra(DURATION, 1000 * 60 * 10);
        mTestName = configParams.getStringExtra(TEST_NAME);
        mContext = context;
        mProgressListener = progressListener.orElseGet(() -> (Optional<String> message) -> {
            Log.v(CrashTest.TAG, message.orElse("."));
        });
        mExecutorService = Executors.newFixedThreadPool(mThreadCount);
        mTestCompletionResults.clear();
    }

    @Override
    public Optional<String> call() {
        mParallelTestComplete = new CountDownLatch(mThreadCount);
        for (int i = 0; i < mThreadCount; i++) {
            Processor testProcessor = createSubTestRunner(mTestList, i);

            activeTests.add(testProcessor);
            mExecutorService.submit(testProcessor);
        }

        return completedSuccessfully();
    }

    private Processor createSubTestRunner(final int[] testList, final int testIndex) {
        final Processor result = new Processor(mContext, new Processor.Callback() {
            @SuppressLint("DefaultLocale")
            @Override
            public void onBenchmarkFinish(boolean ok) {
                notifyProgress("Test '%s': Benchmark #%d completed %s", mTestName, testIndex,
                        ok ? "successfully" : "with failure");
                mTestCompletionResults.add(ok);
                mParallelTestComplete.countDown();
            }

            @Override
            public void onStatusUpdate(int testNumber, int numTests, String modelName) {
                Log.v(CrashTest.TAG,
                        String.format("\"Test '%s': Status update from test #%d, model '%s'",
                                mTestName, testNumber,
                                modelName));
            }
        }, testList);
        result.setUseNNApi(true);
        result.setCompleteInputSet(false);
        return result;
    }

    private void endTests() {
        ExecutorService terminatorsThreadPool = Executors.newFixedThreadPool(activeTests.size());
        List<Future<?>> terminationCommands = new ArrayList<>();
        for (final Processor test : activeTests) {
            // Exit will block until the thread is completed
            terminationCommands.add(terminatorsThreadPool.submit(
                    () -> test.exitWithTimeout(Duration.ofSeconds(20).toMillis())));
        }
        terminationCommands.forEach(terminationCommand -> {
            try {
                terminationCommand.get();
            } catch (ExecutionException e) {
                Log.w(TAG,  "Failure while waiting for completion of tests", e);
            } catch (InterruptedException e) {
                Thread.interrupted();
            }
        });
    }

    @SuppressLint("DefaultLocale")
    void notifyProgress(String messageFormat, Object... args) {
        mProgressListener.testProgress(Optional.of(String.format(messageFormat, args)));
    }

    // This method blocks until the tests complete and returns true if all tests completed
    // successfully
    @SuppressLint("DefaultLocale")
    private Optional<String> completedSuccessfully() {
        try {
            boolean testsEnded = mParallelTestComplete.await(mTestDurationMillis, MILLISECONDS);
            if (!testsEnded) {
                Log.i(TAG,
                        String.format(
                                "Test '%s': Tests are not completed (they might have been "
                                        + "designed to run "
                                        + "indefinitely. Forcing termination.", mTestName));
                endTests();
            }
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }

        // ignoring last result for each thread since it will likely be a killed test
        mTestCompletionResults.remove(mTestCompletionResults.size() - mThreadCount);

        final long failedTestCount = mTestCompletionResults.stream().filter(
                testResult -> !testResult).count();
        if (failedTestCount > 0) {
            String failureMsg = String.format("Test '%s': %d out of %d test failed", mTestName,
                    failedTestCount,
                    mTestCompletionResults.size());
            Log.w(CrashTest.TAG, failureMsg);
            return failure(failureMsg);
        } else {
            Log.i(CrashTest.TAG,
                    String.format("Test '%s': Test completed successfully", mTestName));
            return success();
        }
    }
}