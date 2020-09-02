package my.android.audiobook.helpers;

import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.util.Log;
import android.widget.Toast;

import my.android.audiobook.R;
import my.android.audiobook.data.BookContract;
import my.android.audiobook.data.BookDbHelper;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.channels.FileChannel;

public class Migrator {

    private static String LOG_TAG = Migrator.class.getName();
    private Context mContext;

    public Migrator(Context context) {
        mContext = context;
    }
    /*
     * Import database from the specified db file
     */
    public void importDatabase(File dbFile) {
        try {
            File dbFileShm = new File(dbFile + "-shm");
            File dbFileWal = new File(dbFile + "-wal");
            File[] importFiles = {dbFile, dbFileShm, dbFileWal};

            SQLiteDatabase db = mContext.openOrCreateDatabase(BookDbHelper.DATABASE_NAME, Context.MODE_PRIVATE, null);
            String newDBPath = db.getPath();
            db.close();

            File newDBFile = new File(newDBPath);
            File newDBShm = new File(newDBPath + "-shm");
            File newDBWal = new File(newDBPath + "-wal");
            File[] newFiles = {newDBFile, newDBShm, newDBWal};

            int fileExists = 0;
            for (int i = 0; i < importFiles.length; i++) {
                if (importFiles[i].exists()) {
                    FileChannel src = new FileInputStream(importFiles[i]).getChannel();
                    FileChannel dst = new FileOutputStream(newFiles[i]).getChannel();
                    dst.transferFrom(src, 0, src.size());
                    src.close();
                    dst.close();

                    fileExists++;
                } else {
                    newFiles[i].delete();
                }
            }
            if (fileExists > 0) {
                // Adjust album cover paths to contain only the cover file name to enable
                // import of dbs that were exported in a previous version with the full path names
                // Get the old cover path
                String[] proj = new String[]{
                        BookContract.AlbumEntry._ID,
                        BookContract.AlbumEntry.COLUMN_COVER_PATH};
                Cursor c = mContext.getContentResolver().query(BookContract.AlbumEntry.CONTENT_URI,
                        proj, null, null, null);
                if (c != null) {
                    if (c.getCount() > 0) {
                        c.moveToFirst();
                        while (c.moveToNext()) {
                            String oldCoverPath = c.getString(c.getColumnIndex(BookContract.AlbumEntry.COLUMN_COVER_PATH));
                            int id = c.getInt(c.getColumnIndex(BookContract.AlbumEntry._ID));
                            if (oldCoverPath != null && !oldCoverPath.isEmpty()) {
                                // Replace the old cover path in the database by the new relative path
                                String newCoverPath = new File(oldCoverPath).getName();
                                ContentValues values = new ContentValues();
                                values.put(BookContract.AlbumEntry.COLUMN_COVER_PATH, newCoverPath);
                                Uri albumUri = ContentUris.withAppendedId(BookContract.AlbumEntry.CONTENT_URI, id);
                                mContext.getContentResolver().update(albumUri, values, null, null);
                            }
                        }
                    }
                    c.close();
                }
                Toast.makeText(mContext.getApplicationContext(), R.string.import_success, Toast.LENGTH_LONG).show();
            }

            // Make sure onUpgrade() is called in case a database of version 1 or 2 was imported
            // onUpgrade() is called when getReadableDatabase() or getWriteableDatabase() is called
            // We need a new instance of BookDbHelper here, not completely sure why, something
            // about the version number probably
            BookDbHelper dbHelper = new BookDbHelper(mContext);
            dbHelper.getWritableDatabase();
        } catch (Exception e) {
            Toast.makeText(mContext.getApplicationContext(), R.string.import_fail, Toast.LENGTH_LONG).show();
            Log.e(LOG_TAG, e.getMessage());

        }
    }

    /*
     * Export database to the specified directory
     */
    public void exportDatabase(File directory) {
        try {
            if (directory.canWrite()) {
                String currentDBPath = mContext.openOrCreateDatabase(BookDbHelper.DATABASE_NAME, Context.MODE_PRIVATE, null).getPath();

                File currentDB = new File(currentDBPath);
                File currentDBShm = new File(currentDBPath + "-shm");
                File currentDBWal = new File(currentDBPath + "-wal");
                File[] currentFiles = {currentDB, currentDBShm, currentDBWal};

                String backupDBPath = "audiobook.db";
                File backupDB = new File(directory, backupDBPath);
                File backupDBShm = new File(directory, backupDBPath + "-shm");
                File backupDBWal = new File(directory, backupDBPath + "-wal");
                File[] backupFiles = {backupDB, backupDBShm, backupDBWal};

                int fileExists = 0;
                for (int i = 0; i < currentFiles.length; i++) {
                    if (currentFiles[i].exists()) {
                        FileChannel src = new FileInputStream(currentFiles[i]).getChannel();
                        FileChannel dst = new FileOutputStream(backupFiles[i]).getChannel();
                        dst.transferFrom(src, 0, src.size());
                        src.close();
                        dst.close();

                        fileExists++;
                    }
                }
                if (fileExists > 0) {
                    String successStr = mContext.getResources().getString(R.string.export_success, backupDB.getAbsoluteFile());
                    Toast.makeText(mContext.getApplicationContext(), successStr, Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(mContext.getApplicationContext(), R.string.export_fail, Toast.LENGTH_LONG).show();
                }
            }
        } catch (Exception e) {
            Toast.makeText(mContext.getApplicationContext(), R.string.export_fail, Toast.LENGTH_LONG).show();
            Log.e(LOG_TAG, e.getMessage());
        }
    }
}
