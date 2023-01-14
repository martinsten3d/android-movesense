package se.kth.martsten.externalsensors.io;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;

import se.kth.martsten.externalsensors.model.Sensors;

/**
 * This class contains methods for saving files to local storage.
 */
public class FileIO {

    /**
     * Convert and save a set of angles with timestamps to a JSON file.
     * @param context the applications current context.
     * @param filename the name of the saved file.
     * @param angleDataSet data set containing times paired with angles.
     */
    public static void saveAnglesToJSONFile(Context context, String filename, ArrayList<Sensors.AngleData> angleDataSet) {
        try {
            // Create a new JSONArray to store the accelerometer values in
            JSONArray jsonArray = new JSONArray();

            // Loop through the accelerometers values and add them to the JSONArray
            for (Sensors.AngleData angleData : angleDataSet) {
                // Create a new JSONObject to represent the angle value
                JSONObject jsonObject = new JSONObject();
                jsonObject.put("time", angleData.getTimestamp());
                jsonObject.put("angle", angleData.getAngleValue());

                // Add the JSONObject to the JSONArray
                jsonArray.put(jsonObject);
            }

            // Convert JsonObject to String Format
            String userString = jsonArray.toString();

            // Define the File Path and its Name
            File file = new File(context.getFilesDir(),String.format("%s_%s.json", filename, System.currentTimeMillis()));
            FileWriter fileWriter = new FileWriter(file);
            BufferedWriter bufferedWriter = new BufferedWriter(fileWriter);
            bufferedWriter.write(userString);
            bufferedWriter.close();

        } catch (IOException | JSONException e) {
            e.printStackTrace();
        }
    }
}
