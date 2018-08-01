/**
 * Copyright 2017 The Android Open Source Project
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

#ifndef COM_EXAMPLE_ANDROID_NN_BENCHMARK_RUN_TFLITE_H
#define COM_EXAMPLE_ANDROID_NN_BENCHMARK_RUN_TFLITE_H

#include "tensorflow/contrib/lite/interpreter.h"
#include "tensorflow/contrib/lite/model.h"

#include <unistd.h>
#include <vector>

// Inputs and expected outputs for inference
struct InferenceInOut {
  uint8_t *input;
  size_t input_size;

  uint8_t *output;
  size_t output_size;
};

// Result of a single inference
struct InferenceResult {
  float computeTimeSec;
  float meanSquareError;
  float maxSingleError;
};

class BenchmarkModel {
public:
    explicit BenchmarkModel(const char* modelfile);
    ~BenchmarkModel();

    bool resizeInputTensors(std::vector<int> shape);
    bool setInput(const uint8_t* dataPtr, size_t length);
    void getOutputError(const uint8_t* dataPtr, size_t length, InferenceResult* result);
    bool runInference(bool use_nnapi);

    bool benchmark(const std::vector<InferenceInOut> &inOutData,
                   int inferencesMaxCount,
                   float timeout,
                   std::vector<InferenceResult> *result);


private:
    std::unique_ptr<tflite::FlatBufferModel> mTfliteModel;
    std::unique_ptr<tflite::Interpreter> mTfliteInterpreter;
};


#endif  // COM_EXAMPLE_ANDROID_NN_BENCHMARK_RUN_TFLITE_H
