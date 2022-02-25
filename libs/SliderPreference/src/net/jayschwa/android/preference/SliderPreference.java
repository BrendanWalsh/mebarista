/*
 * Copyright 2012 Jay Weisskopf
 *
 * Licensed under the MIT License (see LICENSE.txt)
 */

package net.jayschwa.android.preference;

import android.content.Context;
import android.content.res.TypedArray;
import android.preference.DialogPreference;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.SeekBar;
import android.widget.TextView;

import java.math.BigDecimal;

/**
 * @author Jay Weisskopf
 */
public class SliderPreference extends DialogPreference {

    private static final String TAG = "SliderPreference";

	protected float mValue, mValueOriginal, mMin, mMax, mResolution, mScale;
    protected String mFormat;

	public SliderPreference(Context context, AttributeSet attrs) {
		super(context, attrs);
		setup(context, attrs);
	}

	public SliderPreference(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		setup(context, attrs);
	}

	private void setup(Context context, AttributeSet attrs) {

        setPersistent(true);
        // shouldPersist();

        //if( getPersistedFloat( -42f ) == -42f ) {

        //    persistFloat( 0f );

        //}

		setDialogLayoutResource(R.layout.slider_preference_dialog);

		TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.SliderPreference);
		try {

            mValueOriginal = mValue = 0;
            mMin = 0;
            mMax = 100;
            mResolution = 0.1f;
            mScale = 1;
            mFormat = "%.2f";

			//setSummary(a.getTextArray(R.styleable.SliderPreference_android_summary));
            // a.get
			mFormat = a.getString(R.styleable.SliderPreference_format);
			mMin = a.getFloat(R.styleable.SliderPreference_minn, 0);
			mMax = a.getFloat(R.styleable.SliderPreference_android_max, 100);
			mResolution = a.getFloat(R.styleable.SliderPreference_resolution, 1);
			mScale = a.getFloat(R.styleable.SliderPreference_scales, 1);

		} catch (Exception e) {

			Log.e("SliderPreference", "Error in attributes");
		}
		a.recycle();

    }

    // TODO implement :
    // valueStorageToDialog
    // valueDialogToStorage
    // valueFormat ( currency symbol, precision etc )
    protected String valueFormat( float value ) {

        if( mFormat != null )
            return String.format(mFormat, value);
        else
            return "" + value;

    }

	@Override
	protected Object onGetDefaultValue(TypedArray a, int index) {

			return a.getFloat(index, 0);

    }

	@Override
	protected void onSetInitialValue(boolean restoreValue, Object defaultValue) {

        try {

            mValueOriginal = mValue = restoreValue ? getPersistedFloat( 0 ) / mScale : (float) defaultValue / mScale;

			persistFloat( mValue * mScale );

        }
        catch( Exception e ) {

            Log.e( TAG, "Types do not match:" + this.getKey() );

        }

    }

	@Override
	public CharSequence getSummary() {

        try {
            return String.format( super.getSummary().toString(), valueFormat( mValue /* mScale */ ) );
        }
        catch( Exception e ) {
            Log.e( TAG, "getSummary error, format: '" + this.getKey() + "'" );
        }

        return "";

	}

	protected float getValueFromSeekbar( int progress ) {

        return mMin + progress * mResolution;

	}

	@Override
	protected View onCreateDialogView() {
		// mSeekBarValue = (int) (mValue * SEEKBAR_RESOLUTION);
		final View view = super.onCreateDialogView();
		SeekBar seekbar = (SeekBar) view.findViewById(R.id.slider_preference_seekbar);

        seekbar.setMax( (int) ( ( mMax - mMin ) / mResolution ) );

		mValue = Math.min( mValue, mMax);
		mValue = Math.max( mValue, mMin );

		seekbar.setProgress( (int) ( ( mValue - mMin ) / mResolution ) );

		TextView min, max;
		min = (TextView) view.findViewById( R.id.min );
		max = (TextView) view.findViewById( R.id.max );

		min.setText( valueFormat( mMin ) );
		max.setText( valueFormat( mMax ) );

        TextView value = (TextView) view.findViewById( R.id.value );
        value.setText( valueFormat( mValue ) );

		seekbar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {

			@Override
			public void onStopTrackingTouch(SeekBar seekBar) {
			}

			@Override
			public void onStartTrackingTouch(SeekBar seekBar) {
			}

			@Override
			public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {

				if (fromUser) {
                    mValue = getValueFromSeekbar( progress );

					TextView value = (TextView) view.findViewById( R.id.value );
					value.setText( valueFormat( mValue ) );
				}

			}
		});
		return view;
	}

	@Override
	protected void onDialogClosed(boolean positiveResult) {

		if ( positiveResult && callChangeListener( mValue * mScale ) ) {

            if( shouldPersist() ) {

                try {

                    persistFloat( mValue * mScale );

                }
                catch( ClassCastException e ) {

                    getEditor().remove(getKey());
                    getEditor().putFloat(getKey(), mValue * mScale);
                    getEditor().commit();

                    //persistFloat(mValue * mScale);

                }

            }

            if ( mValueOriginal != mValue ) {
                notifyChanged();

                mValueOriginal = mValue;
            }

        }

        super.onDialogClosed(positiveResult);

    }

}
