package com.tommihirvonen.exifnotes.Utilities;

// Copyright 2015
// Tommi Hirvonen

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.location.Location;
import android.os.Build;
import android.preference.PreferenceManager;
import android.support.v4.view.ViewCompat;
import android.support.v4.widget.NestedScrollView;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.tommihirvonen.exifnotes.Datastructures.Frame;
import com.tommihirvonen.exifnotes.Datastructures.Lens;
import com.tommihirvonen.exifnotes.Datastructures.Roll;
import com.tommihirvonen.exifnotes.R;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.text.Normalizer;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

/**
 * This class contains utility functions.
 */
public class Utilities {


    public final String[] allApertureValues;
    public final String[] apertureValuesThird;
    public final String[] apertureValuesHalf;
    public final String[] apertureValuesFull;

    private final String[] allShutterValues;
    public final String[] allShutterValuesNoBulb;
    public final String[] shutterValuesThird;
    public final String[] shutterValuesHalf;
    public final String[] shutterValuesFull;

    public Utilities(Context context){
        allApertureValues = new String[]{context.getResources().getString(R.string.NoValue), "1.0", "1.1", "1.2", "1.4", "1.6", "1.8", "2.0", "2.2", "2.5",
                "2.8", "3.2", "3.5", "4.0", "4.5", "5.0", "5.6", "6.3", "6.7", "7.1", "8", "9", "9.5",
                "10", "11", "13", "14", "16", "18", "19", "20", "22", "25", "27", "29", "32", "36", "38",
                "42", "45", "50", "57", "64"};
        apertureValuesThird = new String[]{context.getResources().getString(R.string.NoValue), "1.0", "1.1", "1.2", "1.4", "1.6", "1.8", "2.0", "2.2", "2.5",
                "2.8", "3.2", "3.5", "4.0", "4.5", "5.0", "5.6", "6.3", "7.1", "8", "9",
                "10", "11", "13", "14", "16", "18", "20", "22", "25", "29", "32", "36",
                "42", "45", "50", "57", "64"};
        apertureValuesHalf = new String[]{context.getResources().getString(R.string.NoValue), "1.0", "1.2", "1.4", "1.8", "2.0", "2.5", "2.8", "3.5",
                "4.0", "4.5", "5.6", "6.7", "8", "9.5", "11", "13", "16", "19",
                "22", "27", "32", "38", "45", "64" };
        apertureValuesFull = new String[]{context.getResources().getString(R.string.NoValue), "1.0", "1.4", "2.0", "2.8", "4.0", "5.6", "8", "11",
                "16", "22", "32", "45", "64" };

        allShutterValues = new String[]{context.getResources().getString(R.string.NoValue), "B", "30\"", "25\"", "20\"", "15\"", "13\"", "10\"", "8\"", "6\"", "5\"", "4\"",
                "3.2\"", "3\"", "2.5\"", "2\"", "1.6\"", "1.5\"","1.3\"", "1\"", "0.8\"", "0.7\"", "0.6\"", "1/2", "0.4\"", "1/3", "0.3\"",
                "1/4", "1/5", "1/6", "1/8", "1/10", "1/13", "1/15", "1/20", "1/25",
                "1/30", "1/40", "1/45", "1/50", "1/60", "1/80", "1/90", "1/100", "1/125", "1/160", "1/180", "1/200",
                "1/250", "1/320", "1/350", "1/400", "1/500", "1/640", "1/750", "1/800", "1/1000", "1/1250", "1/1500",
                "1/1600", "1/2000", "1/2500", "1/3000", "1/3200", "1/4000", "1/5000", "1/6000", "1/6400", "1/8000"};
        allShutterValuesNoBulb = new String[]{context.getResources().getString(R.string.NoValue), "30\"", "25\"", "20\"", "15\"", "13\"", "10\"", "8\"", "6\"", "5\"", "4\"",
                "3.2\"", "3\"", "2.5\"", "2\"", "1.6\"", "1.5\"","1.3\"", "1\"", "0.8\"", "0.7\"", "0.6\"", "1/2", "0.4\"", "1/3", "0.3\"",
                "1/4", "1/5", "1/6", "1/8", "1/10", "1/13", "1/15", "1/20", "1/25",
                "1/30", "1/40", "1/45", "1/50", "1/60", "1/80", "1/90", "1/100", "1/125", "1/160", "1/180", "1/200",
                "1/250", "1/320", "1/350", "1/400", "1/500", "1/640", "1/750", "1/800", "1/1000", "1/1250", "1/1500",
                "1/1600", "1/2000", "1/2500", "1/3000", "1/3200", "1/4000", "1/5000", "1/6000", "1/6400", "1/8000"};
        shutterValuesThird = new String[]{context.getResources().getString(R.string.NoValue), "B", "30\"", "25\"", "20\"", "15\"", "13\"", "10\"", "8\"", "6\"", "5\"", "4\"",
                "3.2\"", "2.5\"", "2\"", "1.6\"", "1.3\"", "1\"", "0.8\"", "0.6\"", "1/2", "0.4\"", "0.3\"",
                "1/4", "1/5", "1/6", "1/8", "1/10", "1/13", "1/15", "1/20", "1/25",
                "1/30", "1/40", "1/50", "1/60", "1/80", "1/100", "1/125", "1/160", "1/200",
                "1/250", "1/320", "1/400", "1/500", "1/640", "1/800", "1/1000", "1/1250",
                "1/1600", "1/2000", "1/2500", "1/3200", "1/4000", "1/5000", "1/6400", "1/8000"};
        shutterValuesHalf = new String[]{context.getResources().getString(R.string.NoValue), "B", "30\"", "20\"", "15\"", "10\"", "8\"", "6\"", "4\"", "3\"", "2\"", "1.5\"",
                "1\"", "0.7\"", "1/2", "1/3", "1/4", "1/6", "1/8", "1/10", "1/15", "1/20",
                "1/30", "1/45", "1/60", "1/90", "1/125", "1/180", "1/250", "1/350",
                "1/500", "1/750", "1/1000", "1/1500", "1/2000", "1/3000", "1/4000", "1/6000", "1/8000" };
        shutterValuesFull = new String[]{context.getResources().getString(R.string.NoValue), "B", "30\"", "15\"", "8\"", "4\"", "2\"", "1\"", "1/2", "1/4", "1/8",
                "1/15", "1/30", "1/60", "1/125", "1/250", "1/500", "1/1000", "1/2000", "1/4000", "1/8000" };


    }

    public static final String[] isoValues = {"0", "12", "16", "20", "25", "32", "40", "50", "64", "80", "100", "125", "160", "200",
            "250", "320", "400", "500", "640", "800", "1000",
            "1250", "1600", "2000", "2500", "3200", "4000", "5000", "6400", "8000", "10000", "12800", "16000",
            "20000", "25600"};

    public static final String[] compValues = {"-3", "-2 2/3", "-2 1/3", "-2", "-1 2/3", "-1 1/3", "-1", "-2/3", "-1/3",
                "0",
                "+1/3", "+2/3", "+1", "+1 1/3", "+1 2/3", "+2", "+2 1/3", "+2 2/3", "+3"};

    /**
     * This function shows a general dialog containing a title and a message.
     *
     * @param activity the calling activity
     * @param title the title of the dialog
     * @param message the message of the dialog
     */
    public static void showGeneralDialog(Activity activity, String title, String message){
        AlertDialog.Builder generalDialogBuilder = new AlertDialog.Builder(activity);
        generalDialogBuilder.setTitle(title);
        generalDialogBuilder.setMessage(message);

        generalDialogBuilder.setNeutralButton(R.string.Close, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
            }
        });

        AlertDialog generalDialog = generalDialogBuilder.create();
        generalDialog.show();
        //The dialog needs to be shown first. Otherwise textView will be null.
        TextView textView = (TextView) generalDialog.findViewById(android.R.id.message);
        textView.setTextSize(14);
    }

    /**
     * This function writes a text file.
     * @param file the file to be written to
     * @param text the text to be written in that file
     * @return false if something went wrong, true otherwise
     */
    public static boolean writeTextFile(File file, String text){
        FileOutputStream fOut;
        try {
            fOut = new FileOutputStream(file);

        } catch (FileNotFoundException e) {
            e.printStackTrace();
            return false;
        }

        OutputStreamWriter osw = new OutputStreamWriter(fOut);
        try {
            osw.write(text);
            osw.flush();
            osw.close();
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    /**
     *
     * This function checks the input string for illegal characters.
     *
     * @param input the string to be checked
     * @return String containing a list of the illegal characters found. If no illegal
     * characters were found, the String will be empty.
     */
//    public static String checkReservedChars(String input){
//        String ReservedChars = "|\\?*<\":>/";
//        StringBuilder resultBuilder = new StringBuilder();
//        for ( int i = 0; i < input.length(); ++i ) {
//            Character c = input.charAt(i);
//            if ( ReservedChars.contains(c.toString()) ) {
//                if (resultBuilder.toString().length() > 0) resultBuilder.append(", ");
//                resultBuilder.append(c.toString());
//            }
//        }
//        return resultBuilder.toString();
//    }

    /**
     *
     * This function replaces illegal characters from the input string to make
     * a valid file name string.
     *
     * @param input the string to be handled
     * @return String where the illegal characters are replaced with an underscore
     */
    public static String replaceIllegalChars(String input){
        return input.replaceAll("[|\\\\?*<\":>/]", "_");
    }

    /**
     * Splits a datetime into an ArrayList with date.
     *
     * @param input Datetime string in format YYYY-M-D HH:MM
     * @return ArrayList with three members: { YYYY, M, D }
     */
    public static ArrayList<String> splitDate(String input) {
        String[] items = input.split(" ");
        ArrayList<String> itemList = new ArrayList<>(Arrays.asList(items));
        // { YYYY-M-D, HH:MM }
        String[] items2 = itemList.get(0).split("-");
        itemList = new ArrayList<>(Arrays.asList(items2));
        // { YYYY, M, D }
        return itemList;
    }

    /**
     * Splits a datetime into an ArrayList with time.
     *
     * @param input Datetime string in format YYYY-M-D HH:MM
     * @return ArrayList with two members: { HH, MM }
     */
    public static ArrayList<String> splitTime(String input) {
        String[] items = input.split(" ");
        ArrayList<String> itemList = new ArrayList<>(Arrays.asList(items));
        // { YYYY-M-D, HH:MM }
        String[] items2 = itemList.get(1).split(":");
        itemList = new ArrayList<>(Arrays.asList(items2));
        // { HH, MM }
        return itemList;
    }

    /**
     * This function deletes all the files in a directory
     *
     * @param dir the directory whose files are to be deleted
     */
    @SuppressWarnings("ResultOfMethodCallIgnored")
    public static void purgeDirectory(File dir) {
        for(File file: dir.listFiles()) {
            if (!file.isDirectory()) {
                file.delete();
            }
        }
    }

    /**
     * Legacy method to imitate the ScrollIndicators introduced in Marshmallow.
     * This method seems to be more reliable than the native ScrollIndicator methods.
     * Plus it works across all the targeted Android versions.
     *
     * @param root the root view containing the NestedScrollView element
     * @param content the NestedScrollView element
     * @param indicators ScrollIndicators in bitwise or format, for example ViewCompat.SCROLL_INDICATOR_TOP | ViewCompat.SCROLL_INDICATOR_BOTTOM
     */
    public static void setScrollIndicators(ViewGroup root, final NestedScrollView content,
                                            final int indicators) {

        // Set up scroll indicators (if present).
        View indicatorUp = root.findViewById(R.id.scrollIndicatorUp);
        View indicatorDown = root.findViewById(R.id.scrollIndicatorDown);

        // First, remove the indicator views if we're not set to use them
        if (indicatorUp != null && (indicators & ViewCompat.SCROLL_INDICATOR_TOP) == 0) {
            root.removeView(indicatorUp);
            indicatorUp = null;
        }
        if (indicatorDown != null && (indicators & ViewCompat.SCROLL_INDICATOR_BOTTOM) == 0) {
            root.removeView(indicatorDown);
            indicatorDown = null;
        }

        if (indicatorUp != null || indicatorDown != null) {
            final View top = indicatorUp;
            final View bottom = indicatorDown;

            if (content != null) {
                // We're just showing the ScrollView, set up listener.
                content.setOnScrollChangeListener(
                        new NestedScrollView.OnScrollChangeListener() {
                            @Override
                            public void onScrollChange(NestedScrollView v, int scrollX,
                                                       int scrollY,
                                                       int oldScrollX, int oldScrollY) {
                                manageScrollIndicators(v, top, bottom);
                            }
                        });
                // Set up the indicators following layout.
                content.post(new Runnable() {
                    @Override
                    public void run() {
                        manageScrollIndicators(content, top, bottom);
                    }
                });
            } else {
                // We don't have any content to scroll, remove the indicators.
                if (top != null) {
                    root.removeView(top);
                }
                if (bottom != null) {
                    root.removeView(bottom);
                }
            }
        }
    }

    /**
     * Sets the ScrollIndicator visibility according to the scroll state of the
     * passed NestedScrollView.
     *
     * @param v View of the NestedScrollView
     * @param upIndicator View of the top ScrollIndicator
     * @param downIndicator View of the bottom ScrollIndicator
     */
    private static void manageScrollIndicators(View v, View upIndicator, View downIndicator) {
        // Using canScrollVertically methods only results in severe depression.
        // Instead we use getScrollY methods and avoid the headache entirely.
        // Besides, these methods work the same way on all devices.
        if (upIndicator != null) {
            if (v.getScrollY() == 0) {
                upIndicator.setVisibility(View.INVISIBLE);
            } else {
                upIndicator.setVisibility(View.VISIBLE);
            }
        }
        if (downIndicator != null) {
            // To get the actual height of the entire NestedScrollView, we have to do the following.
            // The ScrollView always has one child. Getting its height returns the true height
            // of the ScrollView.
            NestedScrollView nestedScrollView = (NestedScrollView) v;
            if ( v.getScrollY() == nestedScrollView.getChildAt(0).getHeight() - v.getHeight() ) {
                downIndicator.setVisibility(View.INVISIBLE);
            } else {
                downIndicator.setVisibility(View.VISIBLE);
            }
        }
    }

    /**
     * Method to build a custom AlertDialog title TextView. This way we can imitate
     * the default AlertDialog title and its padding.
     *
     * @param context Context of the application
     * @param titleText the text to be displayed by the generated TextView
     * @return generated TextView object
     */
    @SuppressWarnings("deprecation")
    @SuppressLint("RtlHardcoded")
    public static TextView buildCustomDialogTitleTextView(Context context, String titleText){
        TextView titleTextView = new TextView(context);
        if (Build.VERSION.SDK_INT < 23) titleTextView.setTextAppearance(context, android.R.style.TextAppearance_DeviceDefault_DialogWindowTitle);
        else titleTextView.setTextAppearance(android.R.style.TextAppearance_DeviceDefault_DialogWindowTitle);
        float dpi = context.getResources().getDisplayMetrics().density;
        titleTextView.setPadding((int)(20*dpi), (int)(20*dpi), (int)(20*dpi), (int)(10*dpi));
        titleTextView.setText(titleText);
        titleTextView.setGravity(Gravity.LEFT);
        return titleTextView;
    }

    /**
     * This function is called when the user has selected a sorting criteria.
     */
    public void sortFrameList(Context context, final FilmDbHelper database, ArrayList<Frame> listToSort) {
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(context);
        final Utilities utilities = new Utilities(context);
        int sortId = sharedPref.getInt("FrameSortOrder", 0);
        switch (sortId){
            //Sort by count
            case 0:
                Collections.sort(listToSort, new Comparator<Frame>() {
                    @Override
                    public int compare(Frame o1, Frame o2) {
                        // Negative to reverse the sorting order
                        int count1 = o1.getCount();
                        int count2 = o2.getCount();
                        int result;
                        if (count1 < count2) result = -1;
                        else result = 1;
                        return result;
                    }
                });
                break;

            //Sort by date
            case 1:
                Collections.sort(listToSort, new Comparator<Frame>() {
                    @Override
                    public int compare(Frame o1, Frame o2) {
                        String date1 = o1.getDate();
                        String date2 = o2.getDate();
                        @SuppressLint("SimpleDateFormat") SimpleDateFormat format = new SimpleDateFormat("yyyy-M-d H:m");
                        Date d1 = null;
                        Date d2 = null;
                        try {
                            d1 = format.parse(date1);
                            d2 = format.parse(date2);
                        } catch (ParseException e) {
                            e.printStackTrace();
                        }

                        int result;
                        long diff = 0;
                        //Handle possible NullPointerException
                        if (d1 != null && d2 != null) diff = d1.getTime() - d2.getTime();
                        if (diff < 0 ) result = -1;
                        else result = 1;

                        return result;
                    }
                });
                break;

            //Sort by f-stop
            case 2:
                Collections.sort(listToSort, new Comparator<Frame>() {
                    @Override
                    public int compare(Frame o1, Frame o2) {

                        String aperture1 = o1.getAperture();
                        String aperture2 = o2.getAperture();
                        int pos1 = 0;
                        int pos2 = 0;
                        for (int i = 0; i < utilities.allApertureValues.length; ++i){
                            if (aperture1.equals(utilities.allApertureValues[i])) pos1 = i;
                            if (aperture2.equals(utilities.allApertureValues[i])) pos2 = i;
                        }
                        int result;
                        if (pos1 < pos2) result = -1;
                        else result = 1;
                        return result;
                    }
                });
                break;

            //Sort by shutter speed
            case 3:
                Collections.sort(listToSort, new Comparator<Frame>() {
                    @Override
                    public int compare(Frame o1, Frame o2) {

                        //Shutter speed strings need to be modified so that the sorting
                        //works properly.
                        String shutter1 = o1.getShutter().replace("\"", "");
                        String shutter2 = o2.getShutter().replace("\"", "");
                        int pos1 = 0;
                        int pos2 = 0;
                        for (int i = 0; i < utilities.allShutterValues.length; ++i){
                            if (shutter1.equals(utilities.allShutterValues[i])) pos1 = i;
                            if (shutter2.equals(utilities.allShutterValues[i])) pos2 = i;
                        }
                        int result;
                        if (pos1 < pos2) result = -1;
                        else result = 1;
                        return result;
                    }
                });
                break;

            //Sort by lens
            case 4:
                Collections.sort(listToSort, new Comparator<Frame>() {
                    @Override
                    public int compare(Frame o1, Frame o2) {
                        String s1;
                        Lens lens1;

                        String s2;
                        Lens lens2;

                        if (o1.getLensId() > 0) {
                            lens1 = database.getLens(o1.getLensId());
                            s1 = lens1.getMake() + lens1.getModel();
                        } else s1 = "-1";
                        if (o2.getLensId() > 0) {
                            lens2 = database.getLens(o2.getLensId());
                            s2 = lens2.getMake() + lens2.getModel();
                        } else s2 = "-1";

                        return s1.compareTo(s2);
                    }
                });
                break;
        }
    }

    /**
     * This function creates a string containing the ExifTool commands about the roll
     *
     * @return String containing the ExifTool commands
     */
    public static String createExifToolCmdsString(Context context, FilmDbHelper database, long rollId) {
        StringBuilder stringBuilder = new StringBuilder();

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String artistName = prefs.getString("ArtistName", "");
        String copyrightInformation = prefs.getString("CopyrightInformation", "");
        String exiftoolPath = prefs.getString("ExiftoolPath", "");
        String picturesPath = prefs.getString("PicturesPath", "");

        String exiftoolCmd = "exiftool";
        String artistTag = "-Artist=";
        String copyrightTag = "-Copyright=";
        String cameraMakeTag = "-Make=";
        String cameraModelTag = "-Model=";
        String lensMakeTag = "-LensMake=";
        String lensModelTag = "-LensModel=";
        String dateTag = "-DateTime=";
        String shutterTag = "-ShutterSpeedValue=";
        String apertureTag = "-ApertureValue=";
        String commentTag = "-UserComment=";
        String gpsLatTag = "-GPSLatitude=";
        String gpsLatRefTag = "-GPSLatitudeRef=";
        String gpsLngTag = "-GPSLongitude=";
        String gpsLngRefTag = "-GPSLongitudeRef=";
        String fileEnding = ".jpg";
        String quote = "\"";
        String space = " ";
        String lineSep = System.getProperty("line.separator");

        ArrayList<Frame> frameList = database.getAllFramesFromRoll(rollId);
        Roll roll = database.getRoll(rollId);

        for ( Frame frame : frameList ) {
            if ( exiftoolPath.length() > 0 ) stringBuilder.append(exiftoolPath);
            stringBuilder.append(exiftoolCmd).append(space);
            stringBuilder.append(cameraMakeTag).append(quote).append(database.getCamera(roll.getCamera_id()).getMake()).append(quote).append(space);
            stringBuilder.append(cameraModelTag).append(quote).append(database.getCamera(roll.getCamera_id()).getModel()).append(quote).append(space);
            if ( frame.getLensId() > 0 ) {
                stringBuilder.append(lensMakeTag).append(quote).append(database.getLens(frame.getLensId()).getMake()).append(quote).append(space);
                stringBuilder.append(lensModelTag).append(quote).append(database.getLens(frame.getLensId()).getModel()).append(quote).append(space);
            }
            stringBuilder.append(dateTag).append(quote).append(frame.getDate().replace("-", ":")).append(quote).append(space);
            if ( !frame.getShutter().contains("<") ) stringBuilder.append(shutterTag).append(quote).append(frame.getShutter().replace("\"", "")).append(quote).append(space);
            if ( !frame.getAperture().contains("<") )
                stringBuilder.append(apertureTag).append(quote).append(frame.getAperture()).append(quote).append(space);
            if ( frame.getNote().length() > 0 ) stringBuilder.append(commentTag).append(quote).append(Normalizer.normalize(frame.getNote(), Normalizer.Form.NFD).replaceAll("[^\\p{ASCII}]", "")).append(quote).append(space);

            if ( frame.getLocation().length() > 0 ) {
                String latString = frame.getLocation().substring(0, frame.getLocation().indexOf(" "));
                String lngString = frame.getLocation().substring(frame.getLocation().indexOf(" ") + 1, frame.getLocation().length());
                String latRef;
                if (latString.substring(0, 1).equals("-")) {
                    latRef = "S";
                    latString = latString.substring(1, latString.length());
                } else latRef = "N";
                String lngRef;
                if (lngString.substring(0, 1).equals("-")) {
                    lngRef = "W";
                    lngString = lngString.substring(1, lngString.length());
                } else lngRef = "E";
                latString = Location.convert(Double.parseDouble(latString), Location.FORMAT_SECONDS);
                List<String> latStringList = Arrays.asList(latString.split(":"));
                lngString = Location.convert(Double.parseDouble(lngString), Location.FORMAT_SECONDS);
                List<String> lngStringList = Arrays.asList(lngString.split(":"));

                stringBuilder.append(gpsLatTag).append(quote).append(latStringList.get(0)).append(space).append(latStringList.get(1)).append(space).append(latStringList.get(2)).append(quote).append(space);
                stringBuilder.append(gpsLatRefTag).append(quote).append(latRef).append(quote).append(space);
                stringBuilder.append(gpsLngTag).append(quote).append(lngStringList.get(0)).append(space).append(lngStringList.get(1)).append(space).append(lngStringList.get(2)).append(quote).append(space);
                stringBuilder.append(gpsLngRefTag).append(quote).append(lngRef).append(quote).append(space);
            }

            if ( artistName.length() > 0 ) stringBuilder.append(artistTag).append(quote).append(artistName).append(quote).append(space);
            if ( copyrightInformation.length() > 0 ) stringBuilder.append(copyrightTag).append(quote).append(copyrightInformation).append(quote).append(space);
            if ( picturesPath.contains(" ") ) stringBuilder.append(quote);
            if ( picturesPath.length() > 0 ) stringBuilder.append(picturesPath);
            stringBuilder.append(frame.getCount()).append(fileEnding);
            if ( picturesPath.contains(" ") ) stringBuilder.append(quote);
            stringBuilder.append(";").append(lineSep).append(lineSep);

        }

        return stringBuilder.toString();
    }

    /**
     * This function creates a string which contains csv information about the roll.
     *
     * @return String containing the csv information
     */
    public static String createCsvString(Context context, FilmDbHelper database, long rollId) {

        ArrayList<Frame> frameList = database.getAllFramesFromRoll(rollId);
        Roll roll = database.getRoll(rollId);

        final String separator = ",";
        StringBuilder stringBuilder = new StringBuilder();

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String artistName = prefs.getString("ArtistName", "");
        String copyrightInformation = prefs.getString("CopyrightInformation", "");

        stringBuilder.append("Roll name: ").append(roll.getName()).append("\n");
        stringBuilder.append("Added: ").append(roll.getDate()).append("\n");
        stringBuilder.append("Camera: ").append(database.getCamera(roll.getCamera_id()).getMake()).append(" ").append(database.getCamera(roll.getCamera_id()).getModel()).append("\n");
        stringBuilder.append("Notes: ").append(roll.getNote()).append("\n");
        stringBuilder.append("Artist name: ").append(artistName).append("\n");
        stringBuilder.append("Copyright: ").append(copyrightInformation).append("\n");
        stringBuilder.append("Frame Count").append(separator).append("Date").append(separator).append("Lens").append(separator).append("Shutter").append(separator).append("Aperture").append(separator).append("Notes").append(separator).append("Location").append("\n");

        for ( Frame frame : frameList ) {
            stringBuilder.append(frame.getCount());
            stringBuilder.append(separator);
            stringBuilder.append(frame.getDate());
            stringBuilder.append(separator);
            if ( frame.getLensId() > 0 ) stringBuilder.append(database.getLens(frame.getLensId()).getMake()).append(" ").append(database.getLens(frame.getLensId()).getModel());
            stringBuilder.append(separator);
            if ( !frame.getShutter().contains("<") )stringBuilder.append(frame.getShutter());
            stringBuilder.append(separator);
            if ( !frame.getAperture().contains("<") )
                stringBuilder.append("f").append(frame.getAperture());
            stringBuilder.append(separator);
            if ( frame.getNote().length() > 0 ) stringBuilder.append(frame.getNote());
            stringBuilder.append(separator);
            if ( frame.getLocation().length() > 0 ) {
                String latString = frame.getLocation().substring(0, frame.getLocation().indexOf(" "));
                String lngString = frame.getLocation().substring(frame.getLocation().indexOf(" ") + 1, frame.getLocation().length());
                String latRef;
                if (latString.substring(0, 1).equals("-")) {
                    latRef = "S";
                    latString = latString.substring(1, latString.length());
                } else latRef = "N";
                String lngRef;
                if (lngString.substring(0, 1).equals("-")) {
                    lngRef = "W";
                    lngString = lngString.substring(1, lngString.length());
                } else lngRef = "E";
                latString = Location.convert(Double.parseDouble(latString), Location.FORMAT_SECONDS);
                List<String> latStringList = Arrays.asList(latString.split(":"));
                lngString = Location.convert(Double.parseDouble(lngString), Location.FORMAT_SECONDS);
                List<String> lngStringList = Arrays.asList(lngString.split(":"));

                String space = " ";

                stringBuilder.append(latStringList.get(0)).append("°").append(space).append(latStringList.get(1)).append("\'").append(space).append(latStringList.get(2).replace(',', '.')).append("\"").append(space);

                stringBuilder.append(latRef).append(space);

                stringBuilder.append(lngStringList.get(0)).append("°").append(space).append(lngStringList.get(1)).append("\'").append(space).append(lngStringList.get(2).replace(',', '.')).append("\"").append(space);

                stringBuilder.append(lngRef);
            }
            stringBuilder.append("\n");
        }

        return stringBuilder.toString();
    }

    /**
     * This function is used to convert a Location to a string.
     *
     * @param location Location to be converted
     * @return the converted string
     */
    public static String locationStringFromLocation(final Location location) {
        if (location != null)
            return (Location.convert(location.getLatitude(), Location.FORMAT_DEGREES) + " " + Location.convert(location.getLongitude(), Location.FORMAT_DEGREES)).replace(",", ".");
        else return "";
    }

    /**
     * Gets the current date and time.
     * @return Date and time as a string in format YYYY-M-D H:MM
     */
    public static String getCurrentTime() {
        final Calendar c = Calendar.getInstance();
        int iYear = c.get(Calendar.YEAR);
        int iMonth = c.get(Calendar.MONTH) + 1;
        int iDay = c.get(Calendar.DAY_OF_MONTH);
        int iHour = c.get(Calendar.HOUR_OF_DAY);
        int iMin = c.get(Calendar.MINUTE);
        String current_time;
        if (iMin < 10) {
            current_time = iYear + "-" + iMonth + "-" + iDay + " " + iHour + ":0" + iMin;
        } else current_time = iYear + "-" + iMonth + "-" + iDay + " " + iHour + ":" + iMin;
        return current_time;
    }

}
