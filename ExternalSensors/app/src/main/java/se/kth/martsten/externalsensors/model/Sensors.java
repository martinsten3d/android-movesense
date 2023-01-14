package se.kth.martsten.externalsensors.model;

import java.util.ArrayList;

/**
 * Class for containing and processing Movesense sensor data which is used to calculate angles.
 */
public class Sensors {

    public static final float F = 0.15f;
    public static final float ALPHA = 0.1f;

    private static final int MAX_READINGS = 200;
    private static final int SENSOR_LIMIT = 2300;
    private static final int SENSOR_TIME_LIMIT = 200;

    /**
     * The different types of sensors found in Movesense devices.
     */
    public enum SensorType { Acc, Gyro, Magn, IMU6, IMU6m, IMU9 };

    private final ArrayList<SensorData> sensorData;
    private final ArrayList<AngleData> anglesAcc;
    private final ArrayList<AngleData> anglesAccGyro;
    private float lastGyroAngle = 0;
    private int lastGyroTime = 0;

    /**
     * Create a new empty sensor class.
     */
    public Sensors() {
        this.sensorData = new ArrayList<>();
        anglesAcc = new ArrayList<>();
        anglesAccGyro = new ArrayList<>();
    }

    /**
     * Add new sensor data, or overwrite old sensor data.
     * @param type The type of sensor the data comes from.
     * @param time The timestamp of the sensor data.
     * @param x The X value of 3D data.
     * @param y The Y value of 3D data.
     * @param z The Z value of 3D data.
     * @param record If true - adds the new values to a list, if false - overwrites the previous value.
     */
    public void addSensorDataValue(SensorType type, int time, float x, float y, float z, boolean record) {
        // sanity check for sensor values
        if(x > SENSOR_LIMIT || x < -SENSOR_LIMIT || y > SENSOR_LIMIT || y < -SENSOR_LIMIT || z > SENSOR_LIMIT || z < -SENSOR_LIMIT)
            return;

        for(SensorData sd : sensorData)
            if(sd.sensorType == type) {
                // sanity check for sensor time
                if(Math.abs(time - sd.sensorDataValuesRaw.get(sd.sensorDataValuesRaw.size() - 1).getTime()) > SENSOR_TIME_LIMIT)
                    return;

                sd.addSensorDataValue(time, x, y, z, record);
                return;
            }

        // add a new sensor type
        SensorData newSensorData = new SensorData(type);
        sensorData.add(newSensorData);
        newSensorData.addSensorDataValue(time, x, y, z, record);
    }

    /**
     * Get data from a specific sensor.
     * @param type The type of sensor to get data from.
     * @return A SensorData object which contains a list of sensor readings.
     */
    public SensorData getSensorData(SensorType type) {
        for(SensorData sd : sensorData)
            if(sd.sensorType == type)
                return sd;

        return new SensorData(SensorType.Acc);
    }

    public ArrayList<AngleData> getAnglesAcc() { return new ArrayList<>(anglesAcc); }
    public ArrayList<AngleData> getAnglesAccGyro() { return new ArrayList<>(anglesAccGyro); }
    public float getLastGyroAngle() { return lastGyroAngle; }
    public void resetGyroAngle() { lastGyroAngle = 0; }

    /**
     * Calculate a new angle based on previously added sensor data.
     * @param time The timestamp of the angle calculation.
     * @param record If true - adds the new values to a list, if false - overwrites the previous value.
     */
    public void addCalculatedAngle(int time, boolean record) {
        for(SensorData sd : sensorData) {
            if (sd.sensorType == SensorType.Acc) {
                ArrayList<SensorData.SensorDataValue> sdv = sd.sensorDataValuesFiltered;

                if(!record && anglesAcc.size() > 0) {
                    anglesAcc.clear();
                    anglesAccGyro.clear();
                }

                // calculate add angle based on acceleration
                double angleAcc = Math.toDegrees(Math.atan2(sdv.get(sdv.size() - 1).getZ(), sdv.get(sdv.size() - 1).getY()));
                anglesAcc.add(new AngleData(time, angleAcc));

                for(SensorData sdd : sensorData)
                    if(sdd.sensorType == SensorType.Gyro) {
                        // calculate add angle based on acceleration and gyro
                        lastGyroAngle = lastGyroAngle - ((time - lastGyroTime) / 1000f) * sdd.sensorDataValuesRaw.get(sdd.sensorDataValuesRaw.size() - 1).getX();
                        double complimentaryAngle = ALPHA * angleAcc + (1 - ALPHA) * lastGyroAngle;
                        anglesAccGyro.add(new AngleData(time, complimentaryAngle));
                        lastGyroTime = time;
                    }
            }
        }
    }

    /**
     * Static class to represent all data from a specific sensor type.
     * Contains both raw and filtered data.
     */
    public static class SensorData {

        private final SensorType sensorType;
        private final ArrayList<SensorDataValue> sensorDataValuesRaw;
        private final ArrayList<SensorDataValue> sensorDataValuesFiltered;

        private SensorData(SensorType sensorType) {
            this.sensorType = sensorType;
            this.sensorDataValuesRaw = new ArrayList<>();
            this.sensorDataValuesFiltered = new ArrayList<>();
        }

        /**
         * Add new sensor data, or overwrite old sensor data.
         * @param time The timestamp of the sensor data.
         * @param x The X value of 3D data.
         * @param y The Y value of 3D data.
         * @param z The Z value of 3D data.
         * @param record If true - adds the new values to a list, if false - overwrites the previous value.
         */
        public void addSensorDataValue(int time, float x, float y, float z, boolean record) {
            // always add at least one raw sensor reading
            if(sensorDataValuesRaw.size() == 0 || record) {
                sensorDataValuesRaw.add(new SensorDataValue(time, x, y, z));
                if(sensorDataValuesRaw.size() > MAX_READINGS)
                    sensorDataValuesRaw.remove(0);
            }
            else {
                // not recording, clear array and save only one reading
                if(sensorDataValuesRaw.size() > 1) {
                    sensorDataValuesRaw.clear();
                    sensorDataValuesRaw.add(new SensorDataValue(time, x, y, z));
                }
                else
                    sensorDataValuesRaw.set(0, new SensorDataValue(time, x, y, z));
            }

            // always add at least one raw reading to the filtered values
            if(sensorDataValuesFiltered.size() == 0) {
                sensorDataValuesFiltered.add(new SensorDataValue(time, x, y, z));
                return;
            }

            float filteredX = (F * sensorDataValuesFiltered.get(sensorDataValuesFiltered.size() - 1).getX()) + ((1 - F) * x);
            float filteredY = (F * sensorDataValuesFiltered.get(sensorDataValuesFiltered.size() - 1).getY()) + ((1 - F) * y);
            float filteredZ = (F * sensorDataValuesFiltered.get(sensorDataValuesFiltered.size() - 1).getZ()) + ((1 - F) * z);

            if(record) {
                sensorDataValuesFiltered.add(new SensorDataValue(time, filteredX, filteredY, filteredZ));
                if(sensorDataValuesFiltered.size() > MAX_READINGS)
                    sensorDataValuesFiltered.remove(0);
            }
            else {
                // not recording, clear array and save only one reading
                if(sensorDataValuesFiltered.size() > 1) {
                    sensorDataValuesFiltered.clear();
                    sensorDataValuesFiltered.add(new SensorDataValue(time, x, y, z));
                }
                else {
                    sensorDataValuesFiltered.set(0, new SensorDataValue(time, filteredX, filteredY, filteredZ));
                }
            }
        }

        public ArrayList<SensorDataValue> getSensorDataValuesRaw() {
            return new ArrayList<>(sensorDataValuesRaw);
        }
        public ArrayList<SensorDataValue> getSensorDataValuesFiltered() {
            return new ArrayList<>(sensorDataValuesFiltered);
        }

        /**
         * Class representing one reading from a sensor.
         * Contains a timestamp and the X, Y and Z values from the reading.
         */
        public static class SensorDataValue {
            private final int time;
            private final float x, y, z;

            private SensorDataValue(int time, float x, float y, float z) {
                this.time = time;
                this. x = x;
                this.y = y;
                this.z = z;
            }

            public int getTime() { return time; }
            public float getX() { return x; }
            public float getY() { return y; }
            public float getZ() { return z; }
        }
    }

    /**
     * Class representing an angle calculated from sensor data.
     * Contains a timestamp and a value for the angle in degrees.
     */
    public static class AngleData {
        private final int timestamp;
        private final double angleValue;

        public AngleData(int timestamp, double angleValue) {
            this.timestamp = timestamp;
            this.angleValue = angleValue;
        }

        public int getTimestamp() { return timestamp; }
        public double getAngleValue() { return angleValue; }
    }
}
