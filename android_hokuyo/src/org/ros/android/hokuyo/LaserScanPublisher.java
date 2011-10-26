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

package org.ros.android.hokuyo;

import org.ros.message.Duration;
import org.ros.message.MessageListener;
import org.ros.message.std_msgs.Time;
import org.ros.node.DefaultNodeFactory;
import org.ros.node.Node;
import org.ros.node.NodeConfiguration;
import org.ros.node.NodeMain;
import org.ros.node.parameter.ParameterTree;
import org.ros.node.topic.Publisher;

/**
 * @author damonkohler@google.com (Damon Kohler)
 */
public class LaserScanPublisher implements NodeMain {

  private static final double CLUSTER = 1;
  private static final double SKIP = 0;

  private final Scip20Device scipDevice;

  private Duration timeOffset;
  
  private Node node;
  private Publisher<org.ros.message.sensor_msgs.LaserScan> publisher;
  
  /**
   * We need a way to adjust time stamps because it is not (easily) possible to change 
   * a tablet's clock.
   */
  public LaserScanPublisher(Scip20Device scipDevice) {
    this.scipDevice = scipDevice;
  }

  @Override
  public void main(NodeConfiguration nodeConfiguration) throws Exception {
    node = new DefaultNodeFactory().newNode("android_hokuyo_node", nodeConfiguration);
    ParameterTree params = node.newParameterTree();
    final String laserTopic = params.getString("~laser_topic", "laser");
    final String laserFrame = params.getString("~laser_frame", "laser");
    timeOffset = new Duration(0);
    publisher = node.newPublisher(node.resolveName(laserTopic), "sensor_msgs/LaserScan");
    node.newSubscriber("/wall_clock", "std_msgs/Time", 
        new MessageListener<org.ros.message.std_msgs.Time>() {
          @Override
          public void onNewMessage(Time message) {
            timeOffset = message.data.subtract(node.getCurrentTime());
          }
        });
    scipDevice.reset();
    final Configuration configuration = scipDevice.queryConfiguration();
    scipDevice.calibrateTime();
    node.getLog().info("Calibrated laser time offset: " + scipDevice.getScanOffset());
    scipDevice.startScanning(new LaserScanListener() {
      @Override
      public void onNewLaserScan(LaserScan scan) {
        org.ros.message.sensor_msgs.LaserScan message = 
            node.getMessageFactory().newMessage("sensor_msgs/LaserScan");
        // Some laser scanners have blind areas before and after the actual detection range.
        // These are indicated by the front step and the last step configuration variables.
        // Since the blind values never change, we can just ignore them when creating the 
        // range array.
        message.angle_increment = (float) (CLUSTER * (2.0 * Math.PI) / configuration.getTotalSteps());
        message.angle_min = (configuration.getFirstStep() - configuration.getFrontStep()) * message.angle_increment;
        message.angle_max = (configuration.getLastStep() - configuration.getFrontStep()) * message.angle_increment;
        message.ranges = new float[configuration.getLastStep() - configuration.getFirstStep()];
        for (int i = 0; i < message.ranges.length; i++) {
          message.ranges[i] = (float) (scan.getRanges().get(i + configuration.getFirstStep()) / 1000.0);
        }
        message.time_increment = (float) (60.0 / ((double) configuration.getStandardMotorSpeed() * configuration.getTotalSteps()));
        message.scan_time = (float) (60.0 * (SKIP + 1) / (double) configuration.getStandardMotorSpeed());
        message.range_min = (float) (configuration.getMinimumMeasurment() / 1000.0);
        message.range_max = (float) (configuration.getMaximumMeasurement() / 1000.0);
        message.header.frame_id = laserFrame;
        message.header.stamp = new org.ros.message.Time(scan.getTimeStamp()).add(timeOffset);
        publisher.publish(message);
      }
    });
  }

  @Override
  public void shutdown() {
    scipDevice.shutdown();
  }
}