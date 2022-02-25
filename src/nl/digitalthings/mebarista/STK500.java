package nl.digitalthings.mebarista;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;

public class STK500 {
    private OutputStream outStream = null;
    private InputStream inputStream = null;
    Logx log;

    public STK500( InputStream i, OutputStream o, Logx l ) {

        outStream = o;
        inputStream = i;
        log = l;

    }

    private byte[] sync ( String operation, int n, byte[] data ) throws IOException {

        outStream.write(data);

        return sync( operation, n );

    }

    private byte[] sync ( String operation, byte[] data ) throws IOException {

        outStream.write(data);

        return sync( operation, 0 );

    }

    private byte[] sync ( String operation, int n ) throws IOException {

        outStream.flush();

        int insync = inputStream.read();
        byte[] res = null;

        if( n > 0 ) {
            res = new byte[n];

            inputStream.read( res, 0, n );
        }

        int ok = inputStream.read();
        boolean syn = insync == 0x14 && ok == 0x10;

        if( !syn ) {
            log.logcat("not in sync: " + operation, "v");
            throw new IOException("not in sync: " + operation);
        }

        while( inputStream.available() > 0 )
            inputStream.read();

        return res;

    }

    // Inspired by https://github.com/robokoding/STK500
    public void main ( byte[] program ) throws IOException {

        // Consume anything in input stream
        while (inputStream.available() > 0)
            inputStream.read();

        sync("initial sync", new byte[] { 0x30, 0x20, 0x30, 0x20, 0x30, 0x20, 0x30, 0x20, 0x30, 0x20 } );

        int minor = sync( "minor version", 1, new byte[] { 0x41, (byte) 0x82, 0x20 } )[0];
        int major = sync( "major version", 1, new byte[] { 0x41, (byte) 0x81, 0x20 } )[0];
        log.logcat( "version: " + major + "." + minor, "v" );

        sync( "enter programming mode", new byte[] { 0x50, 0x20 } );

        byte[] signature = sync( "device signature", 3, new byte[] { 0x75, 0x20 } );
        log.logcat( "signature: " + String.format( "%02X", signature[0] ) + String.format( "%02X", signature[1] ) + String.format( "%02X", signature[2] ), "v" );

        log.logcat( "flashing: please wait ...", "v" );
        int programIndex = 0;
        int pages_per_ten_percent = program.length / 128 / 10;
        int pages_programmed = 0;

        while ( programIndex < program.length ) {
            int size = Math.min( program.length - programIndex, 128 );

            sync( "load page address " + programIndex, new byte[]{0x55, (byte) ((programIndex / 2) % 256), (byte) ((programIndex / 2) / 256), 0x20});

            outStream.write( new byte[] { 0x64, 0x00, (byte)size, 0x46 } );
            outStream.write( Arrays.copyOfRange( program, programIndex, programIndex + size ) );
            sync( "write page at " + programIndex, new byte[]{0x20} );

            programIndex += size;
            pages_programmed++;

            if( pages_programmed % pages_per_ten_percent == 0 )
                log.logcat("" + (int)( pages_programmed / pages_per_ten_percent * 10 ) + "%", "v" );
        }

        sync( "leave programming mode", new byte[] { 0x51, 0x20 } );

        log.logcat("Programmed " + programIndex + " of " + program.length + " bytes", "v");

    }

}
