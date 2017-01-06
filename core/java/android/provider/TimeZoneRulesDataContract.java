package android.provider;

import android.annotation.SystemApi;
import android.net.Uri;

/**
 * A set of constants useful for implementing / interacting with a time zone data content provider.
 *
 * @hide
 */
@SystemApi
public final class TimeZoneRulesDataContract {

    private TimeZoneRulesDataContract() {}

    /** The authority for the time zone provider */
    public static final String AUTHORITY = "com.android.timezone";
    /** A content:// style uri to the authority for the contacts provider */
    public static final Uri AUTHORITY_URI = Uri.parse("content://" + AUTHORITY);

    /**
     * The content:// style URI for determining what type of update is available and (optionally)
     * the data.
     *
     * <p>The URI can be queried using
     * {@link android.content.ContentProvider#query(Uri, String[], String, String[], String)};
     * the result will be a cursor with a single row. If the {@link #COLUMN_UPDATE_TYPE}
     * column is {@link #UPDATE_TYPE_INSTALL} the provider can also have
     * {@link android.content.ContentProvider#openFile(Uri, String)} with "r" mode and the data
     * can be read.
     */
    public static final Uri DATA_URI = Uri.withAppendedPath(AUTHORITY_URI, "data");

    /**
     * The column of the {@link #DATA_URI} that provides an int. See
     * {@link #UPDATE_TYPE_NO_OP}, {@link #UPDATE_TYPE_UNINSTALL} and {@link #UPDATE_TYPE_INSTALL}.
     */
    @SystemApi
    public static final String COLUMN_UPDATE_TYPE = "update_type";

    /**
     * An operation type used when the time zone rules on device should be left as they are.
     * Not expected to be used in normal operation but a safe result in the event of an error
     * that cannot be recovered from.
     */
    @SystemApi
    public static final int UPDATE_TYPE_NO_OP = 0;

    /**
     * An operation type used when the current time zone rules on device should be uninstalled,
     * returning to the values held in the system partition.
     */
    @SystemApi
    public static final int UPDATE_TYPE_UNINSTALL = 1;

    /**
     * An operation type used when the current time zone rules on device should be replaced by
     * a new set obtained via the {@link android.content.ContentProvider#openFile(Uri, String)}
     * method.
     */
    @SystemApi
    public static final int UPDATE_TYPE_INSTALL = 2;

}
