package nl.digitalthings.mebarista;

import android.content.Context;
import android.preference.EditTextPreference;
import android.util.AttributeSet;

public class EditTextPreferenceWithSummary extends EditTextPreference {


    public EditTextPreferenceWithSummary(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public EditTextPreferenceWithSummary(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public EditTextPreferenceWithSummary(Context context) {
        super(context);
    }

    @Override
    public String getSummary() {
        return  String.format( super.getSummary().toString(), super.getPersistedString( "" ) ); // super.getSummary().toString() + super.getPersistedString( "" );
    }
}
