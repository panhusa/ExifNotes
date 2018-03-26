package com.tommihirvonen.exifnotes.datastructures;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * The frame class holds the information of one frame.
 */
public class Frame implements Parcelable {

    /**
     * database id
     */
    private long id;

    /**
     * database id of the roll to which this frame belongs
     */
    private long rollId;

    /**
     * frame count number
     */
    private int count;

    /**
     * datetime of exposure in format 'YYYY-M-D H:MM'
     */
    private String date;

    /**
     * database id of the lens used to take this frame
     */
    private long lensId;

    /**
     * shutter speed value in format 1/X, Y" or B, where X and Y are numbers
     */
    private String shutter;

    /**
     * aperture value, number only
     */
    private String aperture;

    /**
     * custom note
     */
    private String note;

    /**
     * latitude and longitude in format '12,3456... 12,3456...'
     */
    private String location;

    /**
     * Formatted address according to location
     */
    private String formattedAddress;

    /**
     * lens's focal length
     */
    private int focalLength;

    /**
     * used exposure compensation in format 0, +/-X or +/-Y/Z where X, Y and Z are numbers
     */
    private String exposureComp;

    /**
     * number of exposures on this frame (multiple exposure)
     */
    private int noOfExposures;

    /**
     * NOT YET IN USE
     */
    private int flashUsed;

    /**
     * NOT YET IN USE
     */
    private String flashPower;

    /**
     * NOT YET IN USE
     */
    private String flashComp;

    /**
     * database id of the filter used
     */
    private long filterId;

    /**
     * NOT YET IN USE
     */
    private int meteringMode;

    /**
     * Filename (relative, not absolute) of the complementary picture stored in external storage
     */
    private String pictureFilename;

    /**
     * empty constructor
     */
    public Frame(){
        // Empty constructor
    }

    // Methods to set members

    /**
     *
     * @param input database id
     */
    public void setId(long input) {
        this.id = input;
    }

    /**
     *
     * @param input database id of the roll to which this frame belongs
     */
    public void setRollId(long input){
        this.rollId = input;
    }

    /**
     *
     * @param input frame count number
     */
    public void setCount(int input){
        this.count = input;
    }

    /**
     *
     * @param input datetime of exposure in format 'YYYY-M-D H:MM'
     */
    public void setDate(String input) {
        this.date = input;
    }

    /**
     *
     * @param input database id of the lens used
     */
    public void setLensId(long input){
        this.lensId = input;
    }

    /**
     *
     * @param input shutter speed value in format 1/X, Y" or B, where X and Y are numbers
     */
    public void setShutter(String input) {
        this.shutter = input;
    }

    /**
     *
     * @param input aperture value, number only
     */
    public void setAperture(String input) {
        this.aperture = input;
    }

    /**
     *
     * @param input custom note
     */
    public void setNote(String input){
        this.note = input;
    }

    /**
     *
     * @param input latitude and longitude in format '12,3456... 12,3456...'
     */
    public void setLocation(String input) {
        this.location = input;
    }

    /**
     *
     * @param input formatted address
     */
    public void setFormattedAddress(String input) {
        this.formattedAddress = input;
    }

    /**
     *
     * @param input lens's focal length
     */
    public void setFocalLength(int input) {
        this.focalLength = input;
    }

    /**
     *
     * @param input used exposure compensation in format 0, +/-X or +/-Y/Z where X, Y and Z are numbers
     */
    public void setExposureComp(String input) {
        this.exposureComp = input;
    }

    /**
     *
     * @param input number of exposures on this frame (multiple exposure)
     */
    public void setNoOfExposures(int input){
        this.noOfExposures = input;
    }

    /**
     *
     * @param input NOT YET IN USE
     */
    public void setFlashUsed(int input){
        this.flashUsed = input;
    }

    /**
     *
     * @param input NOT YET IN USE
     */
    public void setFlashPower(String input){
        this.flashPower = input;
    }

    /**
     *
     * @param input NOT YET IN USE
     */
    public void setFlashComp(String input){
        this.flashComp = input;
    }

    /**
     *
     * @param input database id of the used filter
     */
    public void setFilterId(long input){
        this.filterId = input;
    }

    /**
     *
     * @param input NOT YET IN USE
     */
    public void setMeteringMode(int input){
        this.meteringMode = input;
    }

    /**
     *
     * @param input relative (not absolute) filename of the complementary picture
     */
    public void setPictureFilename(String input) {
        this.pictureFilename = input;
    }

    //Methods to get members

    /**
     *
     * @return database id
     */
    public long getId() {
        return this.id;
    }

    /**
     *
     * @return database id of the roll to which this frame belongs
     */
    public long getRollId(){
        return this.rollId;
    }

    /**
     *
     * @return frame count number
     */
    public int getCount(){
        return this.count;
    }

    /**
     *
     * @return datetime of exposure in format 'YYYY-M-D H:MM'
     */
    public String getDate(){
        return this.date;
    }

    /**
     *
     * @return database id of the lens used
     */
    public long getLensId(){
        return this.lensId;
    }

    /**
     *
     * @return shutter speed value in format 1/X, Y" or B, where X and Y are numbers
     */
    public String getShutter(){
        return this.shutter;
    }

    /**
     *
     * @return aperture value, number only
     */
    public String getAperture(){
        return this.aperture;
    }

    /**
     *
     * @return custom note
     */
    public String getNote(){
        return this.note;
    }

    /**
     *
     * @return latitude and longitude in format '12,3456... 12,3456...'
     */
    public String getLocation(){
        return this.location;
    }

    /**
     *
     * @return formatted address
     */
    public String getFormattedAddress(){
        return this.formattedAddress;
    }

    /**
     *
     * @return lens's focal length
     */
    public int getFocalLength(){
        return this.focalLength;
    }

    /**
     *
     * @return used exposure compensation in format 0, +/-X or +/-Y/Z where X, Y and Z are numbers
     */
    public String getExposureComp(){
        return this.exposureComp;
    }

    /**
     *
     * @return number of exposures on this frame (multiple exposure)
     */
    public int getNoOfExposures(){
        return this.noOfExposures;
    }

    /**
     *
     * @return NOT YET IN USE
     */
    public int getFlashUsed(){
        return this.flashUsed;
    }

    /**
     *
     * @return NOT YET IN USE
     */
    public String getFlashPower(){
        return this.flashPower;
    }

    /**
     *
     * @return NOT YET IN USE
     */
    public String getFlashComp(){
        return this.flashComp;
    }

    /**
     *
     * @return database id of the filter used
     */
    public long getFilterId(){
        return this.filterId;
    }

    /**
     *
     * @return NOT YET IN USE
     */
    public int getMeteringMode(){
        return this.meteringMode;
    }

    /**
     *
     * @return relative (not absolute) filename of the complementary picture stored in external storage
     */
    public String getPictureFilename() {
        return this.pictureFilename;
    }

    //METHODS TO IMPLEMENT THE PARCELABLE CLASS TO PASS OBJECT INSIDE INTENTS

    /**
     * Constructs object from Parcel
     *
     * @param pc parcel object containing Camera's information
     */
    private Frame(Parcel pc){
        this.id = pc.readLong();
        this.rollId = pc.readLong();
        this.count = pc.readInt();
        this.date = pc.readString();
        this.lensId = pc.readLong();
        this.shutter = pc.readString();
        this.aperture = pc.readString();
        this.note = pc.readString();
        this.location = pc.readString();
        this.formattedAddress = pc.readString();
        this.focalLength = pc.readInt();
        this.exposureComp = pc.readString();
        this.noOfExposures = pc.readInt();
        this.flashUsed = pc.readInt();
        this.flashPower = pc.readString();
        this.flashComp = pc.readString();
        this.filterId = pc.readLong();
        this.meteringMode = pc.readInt();
        this.pictureFilename = pc.readString();
    }

    /**
     * Not used
     *
     * @return not used
     */
    @Override
    public int describeContents() {
        return 0;
    }

    /**
     * Writes this object's members to a Parcel given as argument
     *
     * @param parcel Parcel which should be written with this object's members
     * @param i not used
     */
    @Override
    public void writeToParcel(Parcel parcel, int i) {
        parcel.writeLong(id);
        parcel.writeLong(rollId);
        parcel.writeInt(count);
        parcel.writeString(date);
        parcel.writeLong(lensId);
        parcel.writeString(shutter);
        parcel.writeString(aperture);
        parcel.writeString(note);
        parcel.writeString(location);
        parcel.writeString(formattedAddress);
        parcel.writeInt(focalLength);
        parcel.writeString(exposureComp);
        parcel.writeInt(noOfExposures);
        parcel.writeInt(flashUsed);
        parcel.writeString(flashPower);
        parcel.writeString(flashComp);
        parcel.writeLong(filterId);
        parcel.writeInt(meteringMode);
        parcel.writeString(pictureFilename);
    }

    /**
     * Static field used to regenerate object, individually or as arrays
     */
    public static final Parcelable.Creator<Frame> CREATOR = new Parcelable.Creator<Frame>() {
        public Frame createFromParcel(Parcel pc) {
            return new Frame(pc);
        }
        public Frame[] newArray(int size) {
            return new Frame[size];
        }
    };
}
