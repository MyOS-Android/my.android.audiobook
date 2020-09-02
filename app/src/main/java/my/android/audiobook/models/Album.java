package my.android.audiobook.models;

import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;

import my.android.audiobook.data.BookContract;
import my.android.audiobook.utils.Utils;

import java.io.File;
import java.util.ArrayList;

public class Album {

    private long mID = -1;
    private String mTitle;
    private Directory mDirectory;
    private String mCoverPath;
    private long mLastPlayedID;

    private static final String[] mAlbumColumns = new String[]{
            BookContract.AlbumEntry._ID,
            BookContract.AlbumEntry.COLUMN_TITLE,
            BookContract.AlbumEntry.COLUMN_DIRECTORY,
            BookContract.AlbumEntry.COLUMN_COVER_PATH,
            BookContract.AlbumEntry.COLUMN_LAST_PLAYED
    };

    public Album(long id, String title, Directory directory, String coverPath, long lastPlayed) {
        mID = id;
        mTitle = title;
        mDirectory = directory;
        mCoverPath = coverPath;
        mLastPlayedID = lastPlayed;
    }

    public Album(String title, Directory directory, String coverPath) {
        mTitle = title;
        mDirectory = directory;
        mCoverPath = coverPath;
    }

    public Album(String title, Directory directory) {
        mTitle = title;
        mDirectory = directory;
        updateAlbumCover();
    }

    public long getID() {
        return mID;
    }

    public String getTitle() {
        return mTitle;
    }

    public String getRelativeCoverPath() {
        return mCoverPath;
    }

    public String getCoverPath() {
        if (mCoverPath == null) {
            return null;
        }

        return new File(mDirectory.getPath(), mCoverPath).getAbsolutePath();
    }

    public void setDirectory(Directory directory) {
        mDirectory = directory;
    }

    public String getPath() {
        if (mDirectory == null) {
            return null;
        }

        File albumFile;
        if (mDirectory.getType() == Directory.Type.PARENT_DIR) {
            albumFile = new File(mDirectory.getPath(), mTitle);
        } else {
            albumFile = new File(mDirectory.getPath());
        }
        return albumFile.getAbsolutePath();
    }

    public void setLastPlayedID(long id) {
        mLastPlayedID = id;
    }

    public long getLastPlayedID() {
        return mLastPlayedID;
    }

    static public String[] getColumns() {
        return mAlbumColumns;
    }

    /*
     * Set the album cover if it has not yet been set or if the current cover does not exist anymore
     */
    public String updateAlbumCover() {
        if (mCoverPath == null || !(new File(mDirectory.getPath() + File.separator + mCoverPath).exists())) {
            // Search for a cover in the album directory
            File albumDir = new File(mDirectory.getPath() + File.separator + mTitle);
            mCoverPath = Utils.getImagePath(albumDir);
            if (mCoverPath != null) {
                mCoverPath = mCoverPath.replace(mDirectory.getPath(), "");
            }
        }
        return mCoverPath;
    }

    /*
     * Insert album into database
     */
    public long insertIntoDB(Context context) {
        ContentValues values = getContentValues();
        Uri uri = context.getContentResolver().insert(BookContract.AlbumEntry.CONTENT_URI, values);

        if (uri == null) {
            return -1;
        }

        mID = ContentUris.parseId(uri);
        return mID;
    }

    /*
     * Update album in database
     */
    public void updateInDB(Context context) {
        if (mID == -1) {
            return ;
        }
        Uri uri = ContentUris.withAppendedId(BookContract.AlbumEntry.CONTENT_URI, mID);
        ContentValues values = getContentValues();
        context.getContentResolver().update(uri, values, null, null);
    }

    /*
     * Put album column values into content values
     */
    public ContentValues getContentValues() {
        ContentValues values = new ContentValues();
        values.put(BookContract.AlbumEntry.COLUMN_TITLE, mTitle);
        values.put(BookContract.AlbumEntry.COLUMN_DIRECTORY, mDirectory.getID());
        values.put(BookContract.AlbumEntry.COLUMN_COVER_PATH, mCoverPath);
        values.put(BookContract.AlbumEntry.COLUMN_LAST_PLAYED, mLastPlayedID);
        return values;
    }

    /*
     * Retrieve album with given ID from database
     */
    static public Album getAlbumByID(Context context, long id) {
        Uri uri = ContentUris.withAppendedId(BookContract.AlbumEntry.CONTENT_URI, id);
        Cursor c = context.getContentResolver().query(uri, mAlbumColumns, null, null, null);

        // Bail early if the cursor is null
        if (c == null) {
            return null;
        } else if (c.getCount() < 1) {
            c.close();
            return null;
        }

        Album album = null;
        if (c.moveToNext()) {
            album = getAlbumFromPositionedCursor(context, c);
        }
        c.close();

        return album;
    }


    /*
     * Get all albums in the given directory
     */
    public static ArrayList<Album> getAllAlbumsInDirectory(Context context, long directoryId) {
        ArrayList<Album> albums = new ArrayList<>();
        String sel = BookContract.AlbumEntry.COLUMN_DIRECTORY + "=?";
        String[] selArgs = {Long.toString(directoryId)};

        Cursor c = context.getContentResolver().query(BookContract.AlbumEntry.CONTENT_URI,
                mAlbumColumns, sel, selArgs, null, null);

        // Bail early if the cursor is null
        if (c == null) {
            return albums;
        } else if (c.getCount() < 1) {
            c.close();
            return albums;
        }

        while (c.moveToNext()) {
            Album album = getAlbumFromPositionedCursor(context, c);
            albums.add(album);
        }
        c.close();

        return albums;
    }

    private static Album getAlbumFromPositionedCursor(Context context, Cursor c) {
        long id = c.getLong(c.getColumnIndex(BookContract.AlbumEntry._ID));
        String title = c.getString(c.getColumnIndex(BookContract.AlbumEntry.COLUMN_TITLE));
        long directoryId = c.getLong(c.getColumnIndex(BookContract.AlbumEntry.COLUMN_DIRECTORY));
        Directory directory = Directory.getDirectoryByID(context, directoryId);
        String coverPath = c.getString(c.getColumnIndex(BookContract.AlbumEntry.COLUMN_COVER_PATH));
        long lastPlayed = -1;
        if (!c.isNull(c.getColumnIndex(BookContract.AlbumEntry.COLUMN_LAST_PLAYED))) {
            lastPlayed = c.getLong(c.getColumnIndex(BookContract.AlbumEntry.COLUMN_LAST_PLAYED));
        }
        return new Album(id, title, directory, coverPath, lastPlayed);
    }
}
