package se.kth.martsten.externalsensors.uiutils;

import android.content.Context;
import android.widget.Toast;

/**
 * This class contains utility methods that display various messages to the user.
 */
public class MsgUtils {

    public static void showToast(String msg, Context context) {
        Toast toast = Toast.makeText(context, msg, Toast.LENGTH_SHORT);
        toast.show();
    }
}