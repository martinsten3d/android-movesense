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
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.TextView;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import se.kth.martsten.externalsensors.io.FileIO;
import se.kth.martsten.externalsensors.model.Sensors;
import se.kth.martsten.externalsensors.uiutils.MsgUtils;
import se.kth.martsten.externalsensors.utils.MovesenseUtils;
import se.kth.martsten.externalsensors.utils.TypeConverter;

/**
 * NB - this class contains some modified code from @anderslm - https://gits-15.sys.kth.se/anderslm/Ble-Gatt-Movesense-2.0
 *
 * Class representing the controller used when recording angular data from a Movesense sensor.
 * The class interfaces with a Sensors object which holds all sensor data.
 */
@SuppressLint("MissingPermission")
public class RecordActivity extends AppCompatActivity {
    private BluetoothDevice mSelectedDevice = null;
    private BluetoothGatt mBluetoothGatt = null;
    private Handler mHandler;

    private Sensors sensors;

    private Button buttonRecord;
    private boolean recording = false;
    private int firstSensorTime = 0;

    private TextView angleAccText, angleAccGyroText;

    BottomNavigationView bottomNavigationView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_record);
        bottomNavigationView = findViewById(R.id.navigation);
        bottomNavigationView.setOnItemSelectedListener(this::navigationListener);
        bottomNavigationView.getMenu().getItem(1).setChecked(true);

        sensors = new Sensors();

        buttonRecord = findViewById(R.id.button_record);
        buttonRecord.setText(R.string.record);
        buttonRecord.setOnClickListener(view -> {
            updateRecording();
        });

        angleAccText = findViewById(R.id.angle_acc);
        angleAccGyroText = findViewById(R.id.angle_acc_gyro);
        updateSensorValuesUI();

        Intent intent = getIntent();
        mSelectedDevice = intent.getParcelableExtra(SELECTED_DEVICE);
        if (mSelectedDevice == null)
            goToActivity(new Intent(RecordActivity.this, ScanActivity.class));

        mHandler = new Handler();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mSelectedDevice == null) return;

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

    private final CountDownTimer recordTimer = new CountDownTimer(10000,10000) {
        @Override
        public void onTick(long l) { }

        @Override
        public void onFinish() {
            updateRecording();
        }
    };

    private void updateRecording() {
        // start recording
        if(!recording) {
            sensors.resetGyroAngle();
            recording = true;
            recordTimer.cancel();
            recordTimer.start();
            buttonRecord.setText(R.string.save);
        }
        // save recording
        else {
            recording = false;
            recordTimer.cancel();
            buttonRecord.setText(R.string.record);

            MsgUtils.showToast("Recording saved", this);
            FileIO.saveAnglesToJSONFile(this, "angleAcc", sensors.getAnglesAcc());
            FileIO.saveAnglesToJSONFile(this, "angleAccGyro", sensors.getAnglesAccGyro());
        }
    }

    private void updateSensorValuesUI() {
        if(sensors.getAnglesAcc().size() > 0)
            angleAccText.setText(getString(R.string.f_1f, sensors.getAnglesAcc().get(sensors.getAnglesAcc().size() - 1).getAngleValue()));
        if(sensors.getAnglesAccGyro().size() > 0)
            angleAccGyroText.setText(getString(R.string.f_1f, sensors.getAnglesAccGyro().get(sensors.getAnglesAccGyro().size() - 1).getAngleValue()));
    }

    @SuppressLint("NonConstantResourceId")
    private boolean navigationListener(MenuItem menuItem) {
        switch(menuItem.getItemId()) {
            case R.id.navigation_connect:
                goToActivity(new Intent(RecordActivity.this, ScanActivity.class));
                return true;
            case R.id.navigation_record:
                return true;
            case R.id.navigation_graph:
                goToActivity(new Intent(RecordActivity.this, GraphActivity.class));
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
    private void startDataTransmission() {
        // Get the Movesense 2.0 IMU service
        BluetoothGattService movesenseService = mBluetoothGatt.getService(MovesenseUtils.MOVESENSE_2_0_SERVICE);
        if (movesenseService == null) {
            mHandler.post(() -> goToActivity(new Intent(RecordActivity.this, ScanActivity.class)));
            return;
        }

        // Write a command, as a byte array, to the command characteristic, callback onCharacteristicWrite
        BluetoothGattCharacteristic commandChar = movesenseService.getCharacteristic(MovesenseUtils.MOVESENSE_2_0_COMMAND_CHARACTERISTIC);
        byte[] command = TypeConverter.stringToAsciiArray(MovesenseUtils.REQUEST_ID, "Meas/IMU6/13");
        commandChar.setValue(command);
        mBluetoothGatt.writeCharacteristic(commandChar);
    }

    /**
     * NB - modified code from @anderslm - https://gits-15.sys.kth.se/anderslm/Ble-Gatt-Movesense-2.0
     */
    private void stopDataTransmission() {
        // Get the Movesense 2.0 IMU service
        BluetoothGattService movesenseService = mBluetoothGatt.getService(MovesenseUtils.MOVESENSE_2_0_SERVICE);
        if (movesenseService == null) {
            goToActivity(new Intent(RecordActivity.this, ScanActivity.class));
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
                goToActivity(new Intent(RecordActivity.this, ScanActivity.class));
            }
        }

        @Override
        public void onServicesDiscovered(final BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS)
                startDataTransmission();
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            // Enable notifications on data from the sensor. First: Enable receiving
            // notifications on the client side, i.e. on this Android device.
            BluetoothGattService movesenseService = gatt.getService(MovesenseUtils.MOVESENSE_2_0_SERVICE);
            BluetoothGattCharacteristic dataCharacteristic = movesenseService.getCharacteristic(MovesenseUtils.MOVESENSE_2_0_DATA_CHARACTERISTIC);

            // second arg: true, notification; false, indication
            if(!gatt.setCharacteristicNotification(dataCharacteristic, true)) {
                goToActivity(new Intent(RecordActivity.this, ScanActivity.class));
                return;
            }

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
                    // parse and interpret the data, ...
                    int time = TypeConverter.fourBytesToInt(data, 2);
                    if(firstSensorTime == 0)
                        firstSensorTime = time;

                    float accX = TypeConverter.fourBytesToFloat(data, 6);
                    float accY = TypeConverter.fourBytesToFloat(data, 10);
                    float accZ = TypeConverter.fourBytesToFloat(data, 14);
                    float gyroX = TypeConverter.fourBytesToFloat(data, 18);
                    float gyroY = TypeConverter.fourBytesToFloat(data, 22);
                    float gyroZ = TypeConverter.fourBytesToFloat(data, 26);

                    mHandler.post(() -> sensors.addSensorDataValue(Sensors.SensorType.Acc, time - firstSensorTime, accX, accY, accZ, recording));
                    mHandler.post(() -> sensors.addSensorDataValue(Sensors.SensorType.Gyro, time - firstSensorTime, gyroX, gyroY, gyroZ, recording));
                    mHandler.post(() -> sensors.addCalculatedAngle(time - firstSensorTime, recording));

                    mHandler.post(() -> updateSensorValuesUI());
                }
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) { }
    };
}