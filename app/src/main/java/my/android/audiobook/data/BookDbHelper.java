package my.android.audiobook.data;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.preference.PreferenceManager;

import my.android.audiobook.R;
import my.android.audiobook.models.Album;
import my.android.audiobook.models.Directory;

import java.util.ArrayList;

/**
 * Audio Database Helper class
 */

public class BookDbHelper extends SQLiteOpenHelper {
    public static final String DATABASE_NAME = "audio_book.db";

    // Database version. Must be incremented when the database schema is changed.
    private static final int DATABASE_VERSION = 3;

    private static BookDbHelper mInstance = null;
    private Context mContext;

    public BookDbHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
        mContext = context;
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        // Create a String that contains the SQL statement to create the audio file table
        String SQL_CREATE_AUDIO_FILE_TABLE = "CREATE TABLE " + BookContract.AudioEntry.TABLE_NAME + " ("
                + BookContract.AudioEntry._ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + BookContract.AudioEntry.COLUMN_TITLE + " TEXT NOT NULL, "
                + BookContract.AudioEntry.COLUMN_ALBUM + " TEXT NOT NULL, "
                + BookContract.AudioEntry.COLUMN_PATH + " TEXT, "
                + BookContract.AudioEntry.COLUMN_TIME + " INTEGER DEFAULT 0, "
                + BookContract.AudioEntry.COLUMN_COMPLETED_TIME + " INTEGER DEFAULT 0);";

        // Create a String that contains the SQL statement to create the album table
        String SQL_CREATE_ALBUM_TABLE = "CREATE TABLE " + BookContract.AlbumEntry.TABLE_NAME + " ("
                + BookContract.AlbumEntry._ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + BookContract.AlbumEntry.COLUMN_TITLE + " TEXT NOT NULL, "
                + BookContract.AlbumEntry.COLUMN_DIRECTORY + " INTEGER, "
                + BookContract.AlbumEntry.COLUMN_LAST_PLAYED + " INTEGER, "
                + BookContract.AlbumEntry.COLUMN_COVER_PATH + " TEXT);";

        // Create a String that contains the SQL statement to create the bookmark table
        String SQL_CREATE_BOOKMARK_TABLE = "CREATE TABLE " + BookContract.BookmarkEntry.TABLE_NAME + " ("
                + BookContract.BookmarkEntry._ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + BookContract.BookmarkEntry.COLUMN_TITLE + " TEXT NOT NULL, "
                + BookContract.BookmarkEntry.COLUMN_POSITION + " INTEGER, "
                + BookContract.BookmarkEntry.COLUMN_AUDIO_FILE + " INTEGER);";

        // Create a String that contains the SQL statement to create the directory table
        String SQL_CREATE_DIRECTORY_TABLE = "CREATE TABLE IF NOT EXISTS " + BookContract.DirectoryEntry.TABLE_NAME + " ("
                + BookContract.DirectoryEntry._ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + BookContract.DirectoryEntry.COLUMN_PATH + " TEXT NOT NULL, "
                + BookContract.DirectoryEntry.COLUMN_TYPE + " INTEGER);";

        db.execSQL(SQL_CREATE_AUDIO_FILE_TABLE);
        db.execSQL(SQL_CREATE_ALBUM_TABLE);
        db.execSQL(SQL_CREATE_BOOKMARK_TABLE);
        db.execSQL(SQL_CREATE_DIRECTORY_TABLE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int i, int i1) {
        if (i < 2) {
            // Create a String that contains the SQL statement to create the bookmark table
            String SQL_CREATE_BOOKMARK_TABLE = "CREATE TABLE IF NOT EXISTS " + BookContract.BookmarkEntry.TABLE_NAME + " ("
                    + BookContract.BookmarkEntry._ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + BookContract.BookmarkEntry.COLUMN_TITLE + " TEXT NOT NULL, "
                    + BookContract.BookmarkEntry.COLUMN_POSITION + " INTEGER, "
                    + BookContract.BookmarkEntry.COLUMN_AUDIO_FILE + " INTEGER);";
            db.execSQL(SQL_CREATE_BOOKMARK_TABLE);
        }
        if (i < 3) {
            // Create directory table
            String SQL_CREATE_DIRECTORY_TABLE = "CREATE TABLE IF NOT EXISTS " + BookContract.DirectoryEntry.TABLE_NAME + " ("
                    + BookContract.DirectoryEntry._ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + BookContract.DirectoryEntry.COLUMN_PATH + " TEXT NOT NULL, "
                    + BookContract.DirectoryEntry.COLUMN_TYPE + " INTEGER);";
            db.execSQL(SQL_CREATE_DIRECTORY_TABLE);

            // Insert current mDirectory from preferences into directory table
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
            String currentDirPath = prefs.getString(mContext.getString(R.string.preference_filename), "");
            Directory currentDirectory = new Directory(currentDirPath, Directory.Type.PARENT_DIR);
            ContentValues values = currentDirectory.getContentValues();
            // Use direct call to db. Calling Directory.insertIntoDB() yields
            //  java.lang.IllegalStateException: getDatabase called recursively
            long directoryID = db.insert(BookContract.DirectoryEntry.TABLE_NAME, null, values);
            currentDirectory.setID(directoryID);

            // Add directory column to album table
            String SQL_ADD_DIRECTORY_COLUMN = "ALTER TABLE " + BookContract.AlbumEntry.TABLE_NAME + " ADD COLUMN " + BookContract.AlbumEntry.COLUMN_DIRECTORY + " INTEGER";
            db.execSQL(SQL_ADD_DIRECTORY_COLUMN);

            // Add column_last_played to album table
            String SQL_ADD_LAST_PLAYED_COLUMN = "ALTER TABLE " + BookContract.AlbumEntry.TABLE_NAME + " ADD COLUMN " + BookContract.AlbumEntry.COLUMN_LAST_PLAYED + " INTEGER";
            db.execSQL(SQL_ADD_LAST_PLAYED_COLUMN);

            // Populate column_directory in album table
            ArrayList<Album> albums = getAllAlbums(db, currentDirectory);
            for (Album album : albums) {
                ContentValues albumValues = album.getContentValues();
                String selection = BookContract.AlbumEntry._ID + "=?";
                String[] selectionArgs = new String[]{String.valueOf(album.getID())};
                db.update(BookContract.AlbumEntry.TABLE_NAME, albumValues, selection, selectionArgs);
            }
        }
    }

    static BookDbHelper getInstance(Context context) {
        if (mInstance == null) {
            mInstance = new BookDbHelper(context.getApplicationContext());
        }
        return mInstance;
    }

    /*
     * Get all albums from the database
     */
    private ArrayList<Album> getAllAlbums(SQLiteDatabase db, Directory directory) {
        ArrayList<Album> albums = new ArrayList<>();
        Cursor c = db.query(BookContract.AlbumEntry.TABLE_NAME, Album.getColumns(), null, null, null, null, null);

        // Bail early if the cursor is null
        if (c == null) {
            return albums;
        } else if (c.getCount() < 1) {
            c.close();
            return albums;
        }

        while (c.moveToNext()) {
            long id = c.getLong(c.getColumnIndex(BookContract.AlbumEntry._ID));
            String title = c.getString(c.getColumnIndex(BookContract.AlbumEntry.COLUMN_TITLE));
            String coverPath = c.getString(c.getColumnIndex(BookContract.AlbumEntry.COLUMN_COVER_PATH));
            long lastPlayed = -1;
            if (!c.isNull(c.getColumnIndex(BookContract.AlbumEntry.COLUMN_LAST_PLAYED))) {
                lastPlayed = c.getLong(c.getColumnIndex(BookContract.AlbumEntry.COLUMN_LAST_PLAYED));
            }
            Album album = new Album(id, title, directory, coverPath, lastPlayed);
            albums.add(album);
        }
        c.close();

        return albums;
    }
}
