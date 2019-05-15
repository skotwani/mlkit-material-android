/*
 * Copyright 2019 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.firebase.ml.md.productsearch;

import android.content.Context;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.ml.common.modeldownload.FirebaseModelDownloadConditions;
import com.google.firebase.ml.common.modeldownload.FirebaseModelManager;
import com.google.firebase.ml.common.modeldownload.FirebaseRemoteModel;
import com.google.firebase.ml.md.objectdetection.DetectedObject;
import com.google.firebase.ml.vision.FirebaseVision;
import com.google.firebase.ml.vision.common.FirebaseVisionImage;
import com.google.firebase.ml.vision.common.FirebaseVisionImageMetadata;
import com.google.firebase.ml.vision.label.FirebaseVisionImageLabel;
import com.google.firebase.ml.vision.label.FirebaseVisionImageLabeler;
import com.google.firebase.ml.vision.label.FirebaseVisionOnDeviceAutoMLImageLabelerOptions;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.google.firebase.ml.md.Utils.PUBLISHED_MODEL_NAME;
import static com.google.firebase.ml.md.Utils.loadAutoMLModel;

/** A fake search engine to help simulate the complete work flow. */
public class SearchEngine {

  private static final String TAG = "SearchEngine";

  public interface SearchResultListener {
    void onSearchCompleted(DetectedObject object, List<Product> productList);
  }

  private final RequestQueue searchRequestQueue;
  private final ExecutorService requestCreationExecutor;

  public SearchEngine(Context context) {
    searchRequestQueue = Volley.newRequestQueue(context);
    requestCreationExecutor = Executors.newSingleThreadExecutor();
  }

  public void search(DetectedObject object, SearchResultListener listener) {
      FirebaseVisionImageLabeler labeler = loadAutoMLModel(PUBLISHED_MODEL_NAME);

      // TODO: Crops the object image out of the full image is expensive, so do it off the UI thread.
    labeler.processImage(FirebaseVisionImage.fromBitmap(object.getBitmap()))
            .addOnSuccessListener(labels -> {
              try {
                // Sort the labels by confidence
                Collections.sort(labels, (lhs, rhs) -> {
                  if(lhs.getConfidence() > rhs.getConfidence()) return -1;
                  else if(lhs.getConfidence() < rhs.getConfidence()) return 1;
                  else return 0;
                });
              }
              catch (Exception e) {
                e.printStackTrace();
              }

              if (labels.size()>0) {
                FirebaseVisionImageLabel label = labels.get(labels.size()-1);
                String text = label.getText();
                float confidence = label.getConfidence();
                Log.v(TAG, text + " " + confidence);
              }

              List<Product> objectList = new ArrayList<>();
              for (int i = 0; i < labels.size(); i++) {
                objectList.add(
                        new Product(/* imageUrl= */ "", labels.get(i).getText(), "Confidence: " + labels.get(i).getConfidence()));
              }
              listener.onSearchCompleted(object, objectList);

              // ...
            })
        .addOnFailureListener(
            e -> {
              Log.e(TAG, "Failed to create product search request!", e);
              // Remove the below dummy code after your own product search backed hooked up.
              List<Product> productList = new ArrayList<>();

              listener.onSearchCompleted(object, productList);
            });
  }

  public void shutdown() {
    searchRequestQueue.cancelAll(TAG);
    requestCreationExecutor.shutdown();
  }

}
