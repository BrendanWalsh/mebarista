package nl.digitalthings.mebarista;

import android.content.Context;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.util.AttributeSet;

public class ListPreferenceWithSummary extends ListPreference {


    public ListPreferenceWithSummary(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public ListPreferenceWithSummary(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ListPreferenceWithSummary(Context context) {
        super(context);
    }

    @Override
    public String getSummary() {
        return  String.format( super.getSummary().toString(), super.getValue(  ) );

        // super.get
        // super.getSummary().toString() + super.getPersistedString( "" );
    }
}
