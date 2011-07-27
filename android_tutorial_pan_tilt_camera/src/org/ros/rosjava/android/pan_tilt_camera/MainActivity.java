/*
 * Copyright (C) 2011 Google Inc.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package org.ros.rosjava.android.pan_tilt_camera;

import android.app.Activity;
import android.content.Intent;
import android.hardware.SensorManager;
import android.os.Bundle;
import org.ros.address.InetAddressFactory;
import org.ros.message.sensor_msgs.CompressedImage;
import org.ros.node.NodeConfiguration;
import org.ros.node.NodeRunner;
import org.ros.rosjava.android.BitmapFromCompressedImage;
import org.ros.rosjava.android.MasterChooser;
import org.ros.rosjava.android.OrientationPublisher;
import org.ros.rosjava.android.views.RosImageView;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * @author damonkohler@google.com (Damon Kohler)
 */
public class MainActivity extends Activity {

  private final NodeRunner nodeRunner;

  private URI masterUri;
  private RosImageView<CompressedImage> image;
  private OrientationPublisher orientationPublisher;

  public MainActivity() {
    super();
    nodeRunner = NodeRunner.newDefault();
  }

  @SuppressWarnings("unchecked")
  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.main);
    image = (RosImageView<CompressedImage>) findViewById(R.id.image);
    image.setTopicName("/usb_cam/image_raw/compressed");
    image.setMessageType("sensor_msgs/CompressedImage");
    image.setMessageToBitmapCallable(new BitmapFromCompressedImage());
    startActivityForResult(new Intent(this, MasterChooser.class), 0);
  }

  @Override
  protected void onResume() {
    super.onResume();
    if (masterUri != null) {
      NodeConfiguration nodeConfiguration =
          NodeConfiguration.newPublic(InetAddressFactory.newNonLoopback().getHostName(), masterUri);
      orientationPublisher =
          new OrientationPublisher((SensorManager) getSystemService(SENSOR_SERVICE));
      nodeRunner.run(orientationPublisher, nodeConfiguration);
      nodeRunner.run(image, nodeConfiguration);
    }
  }

  @Override
  protected void onPause() {
    super.onPause();
    if (masterUri != null) {
      image.shutdown();
      orientationPublisher.shutdown();
    }
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    if (requestCode == 0 && resultCode == RESULT_OK) {
      try {
        masterUri = new URI(data.getStringExtra("ROS_MASTER_URI"));
      } catch (URISyntaxException e) {
        throw new RuntimeException(e);
      }
    }
  }

}
