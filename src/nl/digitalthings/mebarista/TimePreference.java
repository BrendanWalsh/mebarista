package nl.digitalthings.mebarista;

import android.content.Context;
import android.preference.DialogPreference;
import android.util.AttributeSet;
import android.view.View;
import android.widget.TimePicker;
import android.content.res.TypedArray;
import android.text.format.DateFormat;

import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;

public class TimePreference extends DialogPreference {

    private Calendar calendar;
    private TimePicker picker = null;

    public TimePreference(Context ctxt) {
        this(ctxt, null);
    }

    public TimePreference(Context ctxt, AttributeSet attrs) {
        this(ctxt, attrs, 0);
    }

    public TimePreference(Context ctxt, AttributeSet attrs, int defStyle) {
        super(ctxt, attrs, defStyle);

        setPositiveButtonText( "Set" );
        setNegativeButtonText("Cancel");
        calendar = Calendar.getInstance(); // new GregorianCalendar();
    }

    @Override
    protected View onCreateDialogView() {

        picker = new TimePicker(getContext());
        return (picker);
    }

    @Override
    protected void onBindDialogView(View v) {
        super.onBindDialogView(v);
        picker.setCurrentHour(calendar.get(Calendar.HOUR_OF_DAY));
        picker.setCurrentMinute(calendar.get(Calendar.MINUTE));
    }

    @Override
    protected void onDialogClosed(boolean positiveResult) {
        super.onDialogClosed(positiveResult);

        if (positiveResult) {
            System.out.println( "Selected HOUR " + picker.getCurrentHour() );
            calendar.set(Calendar.HOUR_OF_DAY, picker.getCurrentHour());
            calendar.set(Calendar.MINUTE, picker.getCurrentMinute());


            setSummary(getSummary());
            if (callChangeListener(calendar.getTimeInMillis())) {
                System.out.println( "dialog closed: ");

                Calendar fromMidnight; // = Calendar.getInstance();

                //fromMidnight.setTimeInMillis(calendar.getTimeInMillis());

                fromMidnight = (Calendar)calendar.clone();

                // calendar.setTimeZone( fromMidnight.getTimeZone() );

                fromMidnight.set(Calendar.HOUR_OF_DAY, 0);
                fromMidnight.set(Calendar.MINUTE, 0);
                fromMidnight.set(Calendar.SECOND, 0);
                fromMidnight.set(Calendar.MILLISECOND, 0);

                calendar.set( Calendar.SECOND, 0);
                calendar.set( Calendar.MILLISECOND, 0);

                calendar.set( Calendar.DST_OFFSET, 0); // dat hoeft misschien niet

                // persistLong((calendar.getTimeInMillis() - fromMidnight.getTimeInMillis()) / 1000);

                // persist in seconds!
                int val_seconds = calendar.get( Calendar.HOUR_OF_DAY ) * 60 * 60 + calendar.get( Calendar.MINUTE ) * 60;
                persistInt(val_seconds);
                //persist
                notifyChanged();
            }
        }
    }

    @Override
    protected Object onGetDefaultValue(TypedArray a, int index) {
        return (a.getString(index));
    }

    @Override
    protected void onSetInitialValue(boolean restoreValue, Object defaultValue) {

        if (restoreValue) {
            if (false && defaultValue == null) {
                calendar.setTimeInMillis( getPersistedInt( 0/* System.currentTimeMillis() */ ) * 1000 );
            } else {
                //Long stored_seconds = Long.parseLong(getPersistedString((String) defaultValue));

                int stored_seconds = getPersistedInt(0);
                 int hours = stored_seconds / 3600;

                calendar.set(Calendar.HOUR_OF_DAY, hours );
                calendar.set(Calendar.MINUTE, ( stored_seconds - hours*3600 ) / 60 );
                calendar.set(Calendar.SECOND, 0);
                calendar.set(Calendar.MILLISECOND, 0);

                //calendar.setTimeInMillis( calendar.getTimeInMillis() + stored_millis );
            }
        } else {
            if ( false && defaultValue == null) {
                calendar.setTimeInMillis( System.currentTimeMillis() );
            } else {
                //Long stored_seconds = Long.parseLong(getPersistedString((String) defaultValue));

                // Long stored_seconds = Long.parseLong(getPersistedString((String) defaultValue));

                // int stored_seconds = Integer.parseInt( getPersistedString( "0" ) );

                int stored_seconds = getPersistedInt(0);

                int hours = stored_seconds / 3600;

                calendar.set(Calendar.HOUR_OF_DAY, hours );
                calendar.set(Calendar.MINUTE, ( stored_seconds - hours*3600 ) / 60 );
                calendar.set(Calendar.SECOND, 0);
                calendar.set(Calendar.MILLISECOND, 0);


                // calendar.setTimeInMillis( Long.parseLong((String) defaultValue) * 1000 );
            }
        }
        setSummary(getSummary());
    }

    @Override
    public CharSequence getSummary() {
        if (calendar == null) {
            return null;
        }
        return DateFormat.getTimeFormat(getContext()).format(new Date(calendar.getTimeInMillis()));
    }
}


