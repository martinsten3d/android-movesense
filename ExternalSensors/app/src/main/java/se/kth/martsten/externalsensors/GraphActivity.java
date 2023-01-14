package se.kth.martsten.externalsensors;

import static se.kth.martsten.externalsensors.ScanActivity.SELECTED_DEVICE;

import androidx.appcompat.app.AppCompatActivity;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.Description;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.util.ArrayList;
import java.util.List;

import se.kth.martsten.externalsensors.model.Sensors;
import se.kth.martsten.externalsensors.utils.MovesenseUtils;
import se.kth.martsten.externalsensors.utils.TypeConverter;

/**
 * NB - this class contains some modified code from @anderslm - https://gits-15.sys.kth.se/anderslm/Ble-Gatt-Movesense-2.0
 *
 * Class for representing the controller used when displaying sensor data.
 * The class interfaces with a Sensors object which holds all data.
 */
@SuppressLint("MissingPermission")
public class GraphActivity extends AppCompatActivity {
    private final String IMU_FREQUENCY_DEFAULT = "13";

    private Sensors.SensorType IMU_SENSOR = Sensors.SensorType.Acc;
    private String IMU_FREQUENCY = IMU_FREQUENCY_DEFAULT;

    private BluetoothDevice mSelectedDevice = null;
    private BluetoothGatt mBluetoothGatt = null;
    private Handler mHandler;

    private Sensors sensors;
    private int firstSensorTime = 0;

    // UI for all values
    private View accelerationView, gyroView, magnView;
    private TextView accelerationXText, accelerationYText, accelerationZText;
    private TextView gyroXText, gyroYText, gyroZText;
    private TextView magnXText, magnYText, magnZText;

    private LineChart chartAcc;
    private LineChart chartGyro;
    private LineChart chartMagn;

    BottomNavigationView bottomNavigationView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_graph);

        bottomNavigationView = findViewById(R.id.navigation);
        bottomNavigationView.setOnItemSelectedListener(this::navigationListener);
        bottomNavigationView.getMenu().getItem(2).setChecked(true);

        sensors = new Sensors();

        Spinner spinnerSensors = findViewById(R.id.sensor_spinner);
        ArrayAdapter<CharSequence> sensorAdapter = ArrayAdapter.createFromResource(this,
                R.array.sensors_array, R.layout.spinner_item);
        sensorAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerSensors.setAdapter(sensorAdapter);
        spinnerSensors.setSelection(0, false);
        spinnerSensors.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                String sensor = adapterView.getItemAtPosition(i).toString();
                IMU_SENSOR = Sensors.SensorType.valueOf(sensor);

                stopDataTransmission();
                updateUIVisibility();
                timer.cancel();
                timer.start();
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) { }
        });

        Spinner spinnerFrequencies = findViewById(R.id.frequency_spinner);
        ArrayAdapter<CharSequence> frequencyAdapter = ArrayAdapter.createFromResource(this,
                R.array.frequencies_array, R.layout.spinner_item);
        frequencyAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerFrequencies.setAdapter(frequencyAdapter);
        spinnerFrequencies.setSelection(0, false);
        spinnerFrequencies.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                String freq = adapterView.getItemAtPosition(i).toString();
                System.out.println(freq);
                IMU_FREQUENCY = freq;

                stopDataTransmission();
                updateUIVisibility();
                timer.cancel();
                timer.start();
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) { }
        });

        accelerationView = findViewById(R.id.data_acc);
        accelerationXText = findViewById(R.id.data_acc_x);
        accelerationYText = findViewById(R.id.data_acc_y);
        accelerationZText = findViewById(R.id.data_acc_z);
        gyroView = findViewById(R.id.data_gyro);
        gyroXText = findViewById(R.id.data_gyro_x);
        gyroYText = findViewById(R.id.data_gyro_y);
        gyroZText = findViewById(R.id.data_gyro_z);
        magnView = findViewById(R.id.data_magn);
        magnXText = findViewById(R.id.data_magn_x);
        magnYText = findViewById(R.id.data_magn_y);
        magnZText = findViewById(R.id.data_magn_z);

        chartAcc = findViewById(R.id.chart_acc);
        chartGyro = findViewById(R.id.chart_gyro);
        chartMagn = findViewById(R.id.chart_magn);
        chartAcc.setMaxVisibleValueCount(0);
        chartGyro.setMaxVisibleValueCount(0);
        chartMagn.setMaxVisibleValueCount(0);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            chartAcc.setNoDataTextColor(getColor(R.color.chart_text));
            chartGyro.setNoDataTextColor(getColor(R.color.chart_text));
            chartMagn.setNoDataTextColor(getColor(R.color.chart_text));
        }
        Description descAcc = new Description();
        descAcc.setText("Accelerometer");
        chartAcc.setDescription(descAcc);
        Description descGyro = new Description();
        descGyro.setText("Gyroscope");
        chartGyro.setDescription(descGyro);
        Description descMagn = new Description();
        descMagn.setText("Magnetometer");
        chartMagn.setDescription(descMagn);

        updateUIVisibility();

        Intent intent = getIntent();
        mSelectedDevice = intent.getParcelableExtra(SELECTED_DEVICE);
        if (mSelectedDevice == null)
            goToActivity(new Intent(GraphActivity.this, ScanActivity.class));
        mHandler = new Handler();
    }

    private final CountDownTimer timer = new CountDownTimer(1000, 1000) {
        @Override
        public void onTick(long l) { }

        @Override
        public void onFinish() {
            startDataTransmission(IMU_SENSOR, IMU_FREQUENCY);
        }
    };

    @Override
    protected void onResume() {
        super.onResume();
        if (mSelectedDevice == null)
            goToActivity(new Intent(GraphActivity.this, ScanActivity.class));

        // Connect and register callbacks for bluetooth gatt
        mBluetoothGatt = mSelectedDevice.connectGatt(this, false, mBtGattCallback);
        sensors = new Sensors();
        firstSensorTime = 0;
    }

    @Override
    protected void onPause() {
        super.onPause();
        if(mBluetoothGatt == null) return;

        stopDataTransmission();
        mBluetoothGatt.disconnect();
        try {
            mBluetoothGatt.close();
        } catch (Exception e) {
            // ugly, but this is to handle a bug in some versions in the Android BLE API
        }
    }

    private void updateUIVisibility() {
        accelerationView.setVisibility(View.GONE);
        gyroView.setVisibility(View.GONE);
        magnView.setVisibility(View.GONE);
        chartAcc.setVisibility(View.GONE);
        chartGyro.setVisibility(View.GONE);
        chartMagn.setVisibility(View.GONE);
        switch(IMU_SENSOR) {
            case Acc:
                accelerationView.setVisibility(View.VISIBLE);
                chartAcc.setVisibility(View.VISIBLE);
                break;
            case Gyro:
                gyroView.setVisibility(View.VISIBLE);
                chartGyro.setVisibility(View.VISIBLE);
                break;
            case Magn:
                magnView.setVisibility(View.VISIBLE);
                chartMagn.setVisibility(View.VISIBLE);
                break;
            case IMU6:
                accelerationView.setVisibility(View.VISIBLE);
                gyroView.setVisibility(View.VISIBLE);
                chartAcc.setVisibility(View.VISIBLE);
                chartGyro.setVisibility(View.VISIBLE);
                break;
            case IMU6m:
                accelerationView.setVisibility(View.VISIBLE);
                magnView.setVisibility(View.VISIBLE);
                chartAcc.setVisibility(View.VISIBLE);
                chartMagn.setVisibility(View.VISIBLE);
                break;
            case IMU9:
                accelerationView.setVisibility(View.VISIBLE);
                gyroView.setVisibility(View.VISIBLE);
                magnView.setVisibility(View.VISIBLE);
                chartAcc.setVisibility(View.VISIBLE);
                chartGyro.setVisibility(View.VISIBLE);
                chartMagn.setVisibility(View.VISIBLE);
                break;
        }
    }

    private void updateSensorValuesUI() {
        if(IMU_SENSOR == Sensors.SensorType.Acc || IMU_SENSOR == Sensors.SensorType.IMU6 || IMU_SENSOR == Sensors.SensorType.IMU6m || IMU_SENSOR == Sensors.SensorType.IMU9) {
            ArrayList<Sensors.SensorData.SensorDataValue> sensorDataValues = sensors.getSensorData(Sensors.SensorType.Acc).getSensorDataValuesFiltered();
            if(sensorDataValues.size() > 0) {
                accelerationXText.setText(getString(R.string.x_s, sensorDataValues.get(sensorDataValues.size() - 1).getX()));
                accelerationYText.setText(getString(R.string.y_s, sensorDataValues.get(sensorDataValues.size() - 1).getY()));
                accelerationZText.setText(getString(R.string.z_s, sensorDataValues.get(sensorDataValues.size() - 1).getZ()));
                buildChart(chartAcc, sensorDataValues, "Accelerometer");
            }
        }
        if(IMU_SENSOR == Sensors.SensorType.Gyro || IMU_SENSOR == Sensors.SensorType.IMU6 || IMU_SENSOR == Sensors.SensorType.IMU9) {
            ArrayList<Sensors.SensorData.SensorDataValue> sensorDataValues = sensors.getSensorData(Sensors.SensorType.Gyro).getSensorDataValuesFiltered();
            if(sensorDataValues.size() > 0) {
                gyroXText.setText(getString(R.string.x_s, sensorDataValues.get(sensorDataValues.size() - 1).getX()));
                gyroYText.setText(getString(R.string.y_s, sensorDataValues.get(sensorDataValues.size() - 1).getY()));
                gyroZText.setText(getString(R.string.z_s, sensorDataValues.get(sensorDataValues.size() - 1).getZ()));
                buildChart(chartGyro, sensorDataValues, "Gyroscope");
            }
        }
        if(IMU_SENSOR == Sensors.SensorType.Magn ||IMU_SENSOR == Sensors.SensorType.IMU6m ||IMU_SENSOR == Sensors.SensorType.IMU9) {
            ArrayList<Sensors.SensorData.SensorDataValue> sensorDataValues = sensors.getSensorData(Sensors.SensorType.Magn).getSensorDataValuesFiltered();
            if(sensorDataValues.size() > 0) {
                magnXText.setText(getString(R.string.x_s, sensorDataValues.get(sensorDataValues.size() - 1).getX()));
                magnYText.setText(getString(R.string.y_s, sensorDataValues.get(sensorDataValues.size() - 1).getY()));
                magnZText.setText(getString(R.string.z_s, sensorDataValues.get(sensorDataValues.size() - 1).getZ()));
                buildChart(chartMagn, sensorDataValues, "Magnetometer");
            }
        }
    }

    private void buildChart(LineChart chart, ArrayList<Sensors.SensorData.SensorDataValue> sensorDataValues, String title) {
        List<Entry> entriesX = new ArrayList<>();
        List<Entry> entriesY = new ArrayList<>();
        List<Entry> entriesZ = new ArrayList<>();
        for(int i = 0; i < sensorDataValues.size(); i++) {
            entriesX.add(new Entry(sensorDataValues.get(i).getTime() / 1000f, sensorDataValues.get(i).getX()));
            entriesY.add(new Entry(sensorDataValues.get(i).getTime() / 1000f, sensorDataValues.get(i).getY()));
            entriesZ.add(new Entry(sensorDataValues.get(i).getTime() / 1000f, sensorDataValues.get(i).getZ()));
        }

        LineDataSet dataSetX = new LineDataSet(entriesX, "X"); // add entries to dataset
        LineDataSet dataSetY = new LineDataSet(entriesY, "Y"); // add entries to dataset
        LineDataSet dataSetZ = new LineDataSet(entriesZ, "Z"); // add entries to dataset
        dataSetX.setDrawCircles(false);
        dataSetY.setDrawCircles(false);
        dataSetZ.setDrawCircles(false);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            dataSetX.setColor(getColor(R.color.chart_x));
            dataSetY.setColor(getColor(R.color.chart_y));
            dataSetZ.setColor(getColor(R.color.chart_z));
        }

        List<ILineDataSet> dataSets = new ArrayList<>();
        dataSets.add(dataSetX);
        dataSets.add(dataSetY);
        dataSets.add(dataSetZ);

        LineData data = new LineData(dataSets);
        chart.setData(data);
        chart.setContentDescription(title);
        chart.invalidate();
    }

    @SuppressLint("NonConstantResourceId")
    private boolean navigationListener(MenuItem menuItem) {
        switch(menuItem.getItemId()) {
            case R.id.navigation_connect:
                goToActivity(new Intent(GraphActivity.this, ScanActivity.class));
                return true;
            case R.id.navigation_graph:
                return true;
            case R.id.navigation_record:
                goToActivity(new Intent(GraphActivity.this, RecordActivity.class));
                return true;
        }
        return false;
    }

    private void goToActivity(Intent intent) {
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.putExtra(SELECTED_DEVICE, mSelectedDevice);
        startActivity(intent);
        finish();
    }

    /**
     * NB - modified code from @anderslm - https://gits-15.sys.kth.se/anderslm/Ble-Gatt-Movesense-2.0
     */
    private void startDataTransmission(Sensors.SensorType sensor, String freq) {
        // Get the Movesense 2.0 IMU service
        BluetoothGattService movesenseService = mBluetoothGatt.getService(MovesenseUtils.MOVESENSE_2_0_SERVICE);
        if (movesenseService == null) {
            mHandler.post(() -> goToActivity(new Intent(GraphActivity.this, ScanActivity.class)));
            return;
        }

        // Write a command, as a byte array, to the command characteristic, callback: onCharacteristicWrite
        BluetoothGattCharacteristic commandChar = movesenseService.getCharacteristic(MovesenseUtils.MOVESENSE_2_0_COMMAND_CHARACTERISTIC);
        byte[] command = TypeConverter.stringToAsciiArray(MovesenseUtils.REQUEST_ID, String.format("Meas/%s/%s", sensor.name(), freq));
        commandChar.setValue(command);
        sensors = new Sensors();
        firstSensorTime = 0;
        mBluetoothGatt.writeCharacteristic(commandChar);
    }

    /**
     * NB - modified code from @anderslm - https://gits-15.sys.kth.se/anderslm/Ble-Gatt-Movesense-2.0
     */
    private void stopDataTransmission() {
        // Get the Movesense 2.0 IMU service
        BluetoothGattService movesenseService = mBluetoothGatt.getService(MovesenseUtils.MOVESENSE_2_0_SERVICE);
        if (movesenseService == null) {
            goToActivity(new Intent(GraphActivity.this, ScanActivity.class));
            return;
        }

        // Write a command, as a byte array, to the command characteristic, callback: onCharacteristicWrite
        BluetoothGattCharacteristic commandChar = movesenseService.getCharacteristic(MovesenseUtils.MOVESENSE_2_0_COMMAND_CHARACTERISTIC);
        byte[] command = new byte[] { MovesenseUtils.MOVESENSE_RESPONSE, MovesenseUtils.REQUEST_ID };
        commandChar.setValue(command);
        mBluetoothGatt.writeCharacteristic(commandChar);
    }

    /**
     * NB - modified code from @anderslm - https://gits-15.sys.kth.se/anderslm/Ble-Gatt-Movesense-2.0
     * Callbacks for bluetooth gatt changes/updates
     * The documentation is not always clear, but most callback methods seems to
     * be executed on a worker thread - hence use a Handler when updating the ui.
     */
    private final BluetoothGattCallback mBtGattCallback = new BluetoothGattCallback() {

        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothGatt.STATE_CONNECTED) {
                mBluetoothGatt = gatt;
                gatt.discoverServices();
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                mBluetoothGatt = null;
                mHandler.post(() -> goToActivity(new Intent(GraphActivity.this, ScanActivity.class)));
            }
        }

        @Override
        public void onServicesDiscovered(final BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS)
                startDataTransmission(IMU_SENSOR, IMU_FREQUENCY);
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            // Enable notifications on data from the sensor. First: Enable receiving
            // notifications on the client side, i.e. on this Android device.
            BluetoothGattService movesenseService = gatt.getService(MovesenseUtils.MOVESENSE_2_0_SERVICE);
            BluetoothGattCharacteristic dataCharacteristic = movesenseService.getCharacteristic(MovesenseUtils.MOVESENSE_2_0_DATA_CHARACTERISTIC);

            // second arg: true, notification; false, indication
            if(!gatt.setCharacteristicNotification(dataCharacteristic, true)) {
                goToActivity(new Intent(GraphActivity.this, ScanActivity.class));
                return;
            }

            // Second: set enable notification server side (sensor)
            BluetoothGattDescriptor descriptor = dataCharacteristic.getDescriptor(MovesenseUtils.CLIENT_CHARACTERISTIC_CONFIG);
            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            gatt.writeDescriptor(descriptor);
        }

        @Override
        public void onDescriptorWrite(final BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) { }

        /**
         * Callback called on characteristic changes, e.g. when a sensor data value is changed.
         * This is where we receive notifications on new sensor data.
         */
        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            // if response and id matches
            if (MovesenseUtils.MOVESENSE_2_0_DATA_CHARACTERISTIC.equals(characteristic.getUuid())) {
                byte[] data = characteristic.getValue();
                if (data[0] == MovesenseUtils.MOVESENSE_RESPONSE && data[1] == MovesenseUtils.REQUEST_ID) {
                    int len = data.length;
                    int sensorAmount = 1;

                    switch(IMU_SENSOR) {
                        case IMU6:
                        case IMU6m:
                            sensorAmount = 2;
                        break;
                        case IMU9:
                            sensorAmount = 3;
                        break;
                    }

                    // calculate how many readings are in the packet
                    int readings = (len - 6) / sensorAmount / 12;
                    int time = TypeConverter.fourBytesToInt(data, 2);
                    if(firstSensorTime == 0)
                        firstSensorTime = time;

                    float accX = 0, accY = 0, accZ = 0;
                    float gyroX = 0, gyroY = 0, gyroZ = 0;
                    float magnX = 0, magnY = 0, magnZ = 0;

                    for(int i = 0; i < readings; i++) {
                        if(IMU_SENSOR == Sensors.SensorType.Acc) {
                            accX += TypeConverter.fourBytesToFloat(data, 6 + (i*12));
                            accY += TypeConverter.fourBytesToFloat(data, 10 + (i*12));
                            accZ += TypeConverter.fourBytesToFloat(data, 14 + (i*12));
                        }
                        else if(IMU_SENSOR == Sensors.SensorType.Gyro) {
                            gyroX += TypeConverter.fourBytesToFloat(data, 6 + (i*12));
                            gyroY += TypeConverter.fourBytesToFloat(data, 10 + (i*12));
                            gyroZ += TypeConverter.fourBytesToFloat(data, 14 + (i*12));

                        }
                        else if(IMU_SENSOR == Sensors.SensorType.Magn) {
                            magnX += TypeConverter.fourBytesToFloat(data, 6 + (i*12));
                            magnY += TypeConverter.fourBytesToFloat(data, 10 + (i*12));
                            magnZ += TypeConverter.fourBytesToFloat(data, 14 + (i*12));

                        }
                        else if(IMU_SENSOR == Sensors.SensorType.IMU6) {
                            accX += TypeConverter.fourBytesToFloat(data, 6 + (i*12));
                            accY += TypeConverter.fourBytesToFloat(data, 10 + (i*12));
                            accZ += TypeConverter.fourBytesToFloat(data, 14 + (i*12));
                            gyroX += TypeConverter.fourBytesToFloat(data, 18 + (i*16));
                            gyroY += TypeConverter.fourBytesToFloat(data, 22 + (i*16));
                            gyroZ += TypeConverter.fourBytesToFloat(data, 26 + (i*16));
                        }
                        else if(IMU_SENSOR == Sensors.SensorType.IMU6m) {
                            accX += TypeConverter.fourBytesToFloat(data, 6 + (i*12));
                            accY += TypeConverter.fourBytesToFloat(data, 10 + (i*12));
                            accZ += TypeConverter.fourBytesToFloat(data, 14 + (i*12));
                            magnX += TypeConverter.fourBytesToFloat(data, 18 + (i*16));
                            magnY += TypeConverter.fourBytesToFloat(data, 22 + (i*16));
                            magnZ += TypeConverter.fourBytesToFloat(data, 26 + (i*16));
                        }
                        else {
                            accX += TypeConverter.fourBytesToFloat(data, 6 + (i*12));
                            accY += TypeConverter.fourBytesToFloat(data, 10 + (i*12));
                            accZ += TypeConverter.fourBytesToFloat(data, 14 + (i*12));
                            gyroX += TypeConverter.fourBytesToFloat(data, 18 + (i*16));
                            gyroY += TypeConverter.fourBytesToFloat(data, 22 + (i*16));
                            gyroZ += TypeConverter.fourBytesToFloat(data, 26 + (i*16));
                            magnX += TypeConverter.fourBytesToFloat(data, 30 + (i*20));
                            magnY += TypeConverter.fourBytesToFloat(data, 34 + (i*20));
                            magnZ += TypeConverter.fourBytesToFloat(data, 38 + (i*20));
                        }
                    }

                    if(IMU_SENSOR == Sensors.SensorType.Acc || IMU_SENSOR == Sensors.SensorType.IMU6 || IMU_SENSOR == Sensors.SensorType.IMU6m || IMU_SENSOR == Sensors.SensorType.IMU9) {
                        float finalAccX = accX / readings;
                        float finalAccY = accY / readings;
                        float finalAccZ = accZ / readings;
                        mHandler.post(() -> sensors.addSensorDataValue(Sensors.SensorType.Acc, time - firstSensorTime, finalAccX, finalAccY, finalAccZ, true));
                    }
                    if(IMU_SENSOR == Sensors.SensorType.Gyro || IMU_SENSOR == Sensors.SensorType.IMU6 || IMU_SENSOR == Sensors.SensorType.IMU9) {
                        float finalGyroX = gyroX / readings;
                        float finalGyroY = gyroY / readings;
                        float finalGyroZ = gyroZ / readings;
                        mHandler.post(() -> sensors.addSensorDataValue(Sensors.SensorType.Gyro, time - firstSensorTime, finalGyroX, finalGyroY, finalGyroZ, true));
                    }
                    if(IMU_SENSOR == Sensors.SensorType.Magn || IMU_SENSOR == Sensors.SensorType.IMU9) {
                        float finalMagnX = magnX / readings;
                        float finalMagnY = magnY / readings;
                        float finalMagnZ = magnZ / readings;
                        mHandler.post(() -> sensors.addSensorDataValue(Sensors.SensorType.Magn, time - firstSensorTime, finalMagnX, finalMagnY, finalMagnZ, true));
                    }

                    mHandler.post(() -> updateSensorValuesUI());
                }
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) { }
    };
}
