package dev.edgestyle.module;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.File;
import java.io.FileNotFoundException;

public final class ShareProvider extends ContentProvider {
    public boolean onCreate(){return true;}
    private File file(Uri uri)throws FileNotFoundException {
        try {
            File root=new File(getContext().getCacheDir(),"imports").getCanonicalFile();
            File file=new File(root,uri.getLastPathSegment()).getCanonicalFile();
            if(!file.getParentFile().equals(root)||!file.isFile())throw new FileNotFoundException();
            return file;
        }catch(java.io.IOException e){throw new FileNotFoundException();}
    }
    public ParcelFileDescriptor openFile(Uri uri,String mode)throws FileNotFoundException {
        if(!"r".equals(mode))throw new FileNotFoundException();
        return ParcelFileDescriptor.open(file(uri),ParcelFileDescriptor.MODE_READ_ONLY);
    }
    public Cursor query(Uri uri,String[] projection,String selection,String[] args,String sort) {
        MatrixCursor cursor=new MatrixCursor(new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE});
        try{File f=file(uri);cursor.addRow(new Object[]{f.getName(),f.length()});}catch(FileNotFoundException ignored){}
        return cursor;
    }
    public String getType(Uri uri){return "application/octet-stream";}
    public Uri insert(Uri uri,ContentValues values){throw new UnsupportedOperationException();}
    public int delete(Uri uri,String selection,String[] args){return 0;}
    public int update(Uri uri,ContentValues values,String selection,String[] args){return 0;}
}
