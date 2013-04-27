package com.andrewkeeton.divide.and.conquer.card.shuffler;

import java.util.ArrayList;
import java.util.List;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.TextView.OnEditorActionListener;
import static junit.framework.Assert.*;

import com.bugsense.trace.BugSenseHandler;

public class SetupFragment extends Fragment {
	public static final String KEY_SETTING_DECK_SIZE = "setting_deck_size";
	public static final String KEY_SETTING_USE_CUSTOM_SPEED = "setting_use_custom_speed";
	public static final String KEY_SETTING_PRESET_SPEED = "setting_preset_speed";
	public static final String KEY_SETTING_CUSTOM_SPEED = "setting_custom_speed";
	public static final int SPEED_FAST = 80;
	public static final int SPEED_MEDIUM = 60;
	public static final int SPEED_SLOW = 40;
	
	private Resources mRes;
	private Activity mActivity;
	private SharedPreferences mSharedPrefs;
	
	private EditText mEditTextDeckSize;
	private TextView mTextViewSpeed;
	private RadioButton mRadioButtonPresetSpeed;
	private Spinner mSpinnerPresetSpeed;
	private RadioButton mRadioButtonCustomSpeed;
	private EditText mEditTextCustomSpeed;
	private TextView mTextViewEstimatedTime;
	private Button mButtonResume;
	private Button mButtonNew;
	
	private Handler mHandlerSpeedPreview = new Handler();
	
	private List<RadioButton> mRadioButtonsSpeed = new ArrayList<RadioButton>();
	
	private int mSpeedCardsPerMinute = 0;	// Speed of cards placed into piles.
	private int mDelayPeriodMillis = 0;
	private int mDeckSize = 0;				// Number of cards in the deck.
	private float mTextViewSpeedSaturation = 0.0f;
	private int mEstimatedTime = 0;
	
	static public double periodMillisecondsFromSpeedPerMinute(int speedPerMinute) {
		return 60.0 * 1000.0 / ((double) speedPerMinute);
	}
	
	/**
	 * Updates the animation on the speed preview.
	 */
	private Runnable mTaskUpdateSpeedPreview = new Runnable() {
		
		// Saturate the "Speed" TextView in time with the speed.
		public void run() {
			mTextViewSpeedSaturation += (float) MainActivity.DELAY_ANIMATION / (float) mDelayPeriodMillis;
			
			if (mTextViewSpeedSaturation > 1.0f) {
				mTextViewSpeedSaturation = 0.0f;
			}
			
			int color = mRes.getColor(R.color.holo_blue_light);
			float hsvColor[] = new float[3];
			
			Color.colorToHSV(color, hsvColor);
			hsvColor[1] = mTextViewSpeedSaturation;
			
			color = Color.HSVToColor(hsvColor);
			mTextViewSpeed.setTextColor(color);
			mTextViewSpeed.setShadowLayer(mTextViewSpeedSaturation * 4.0f, 0.0f, 0.0f, color);
			
			mHandlerSpeedPreview.removeCallbacks(this);
			mHandlerSpeedPreview.postDelayed(this, MainActivity.DELAY_ANIMATION);
		}
	};
	
	private void updateDeckSizeFromEditText(EditText editText) {
		setDeckSize(MainActivity.numberFromEditText(editText, 0), false);
	}
	
	private TextWatcher mTextWatcherEditTextDeckSize = new TextWatcher() {

		public void afterTextChanged(Editable s) {
			if (s.length() == 0) {
				setDeckSize(0, false);
			} else {
				updateDeckSizeFromEditText(mEditTextDeckSize);
			}
		}

		public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
		public void onTextChanged(CharSequence s, int start, int before, int count) {}
	};
	
	private OnCheckedChangeListener mOnCheckedChangePresetSpeed = new OnCheckedChangeListener() {
		
		public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
			if (isChecked) {
				processRadioButtonClick(mRadioButtonsSpeed, buttonView);
				
				mSpinnerPresetSpeed.setEnabled(true);
				updatePresetSpeedFromSpinner(mSpinnerPresetSpeed, mSpinnerPresetSpeed.getSelectedItemPosition());
				mEditTextCustomSpeed.setEnabled(false);
			}
		}
	};
	
	// Must match the order of @array/speeds in strings.xml.
	public enum Speeds { FAST, MEDIUM, SLOW };
	
	private void updatePresetSpeedFromSpinner(Spinner spinner, int selectionPos) {
		
			switch(Speeds.values()[selectionPos]) {
			case FAST:
				setSpeed(SPEED_FAST, true);
				break;
			case MEDIUM:
				setSpeed(SPEED_MEDIUM, true);
				break;
			case SLOW:
				setSpeed(SPEED_SLOW, true);
				break;
			default:
				fail();
				break;
			}
	}
	
	/**
	 * Converts the user's speed selection into an integer period.
	 */
	private OnItemSelectedListener mOnItemSelectedSpinnerPresetSpeed = new OnItemSelectedListener() {
		
		public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
			if (mRadioButtonPresetSpeed.isChecked()) {
				updatePresetSpeedFromSpinner(mSpinnerPresetSpeed, pos);
			}
		}
		
		public void onNothingSelected(AdapterView<?> parent) {
		}
	};
	
	private OnCheckedChangeListener mOnCheckedChangeCustomSpeed = new OnCheckedChangeListener() {
		
		public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
			if (isChecked) {
				processRadioButtonClick(mRadioButtonsSpeed, buttonView);
				
				mSpinnerPresetSpeed.setEnabled(false);
				mEditTextCustomSpeed.setEnabled(true);
			}
		}
	};
	
	private TextWatcher mTextWatcherEditTextCustomSpeed = new TextWatcher() {

		public void afterTextChanged(Editable s) {
			setSpeed(MainActivity.numberFromString(s.toString(), 0), false);
		}

		public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
		public void onTextChanged(CharSequence s, int start, int before, int count) {}
	};
	
	private OnClickListener mOnClickButtonResume = new OnClickListener() {
		
		public void onClick(View v) {
			MainActivity.hideSoftKeyboard(mActivity);
			
			if (mSharedPrefs.getBoolean(ShufflerFragment.KEY_RESUMABLE, false)) {
				mOnDoneWithSetupListener.onDoneWithSetup(mDeckSize, mDelayPeriodMillis, false);
			}
		}
	};
	
	private OnClickListener mOnClickButtonNew = new OnClickListener() {
		
			public void onClick(View v) {
				MainActivity.hideSoftKeyboard(mActivity);
				
				boolean abort = false;
				if (mDeckSize < 2) {
					Toast.makeText(mActivity, R.string.deck_size_warning, Toast.LENGTH_LONG).show();
					abort = true;
				}
				
				if (mSpeedCardsPerMinute < 1) {
					Toast.makeText(mActivity, R.string.speed_warning, Toast.LENGTH_LONG).show();
					abort = true;
				}
				
				if (!abort) {
					mOnDoneWithSetupListener.onDoneWithSetup(mDeckSize, mDelayPeriodMillis, true);
				}
			}
	};
	
	/**
	 * Generic EditText listener to force the EditText to lose focus and hide the soft keyboard.
	 */
	private OnEditorActionListener mOnEditorActionEditText = new OnEditorActionListener() {
		
		public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
			boolean handled = false;
			
			// When the user clicks done, clear the focus on the edit text and hide the keyboard.
			if (actionId == EditorInfo.IME_ACTION_DONE) {
				EditText editText = (EditText) v;
				editText.clearFocus();
				
				InputMethodManager inputMethodManager = (InputMethodManager)
						v.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
				inputMethodManager.hideSoftInputFromWindow(editText.getWindowToken(), 0);
				
				handled = true;
			}
			
			return handled;
		}
	};
	
	/**
	 * Unchecks all buttons except the one that was clicked.
	 * @param buttons List of buttons in the affected group.
	 * @param buttonClicked Button that was clicked.
	 */
	private void processRadioButtonClick(List<RadioButton> buttons, CompoundButton buttonClicked) {
		for (RadioButton button : buttons) {
			if (button != buttonClicked) {
				button.setChecked(false);
			}
		}
	}
	
	/**
	 * The parent activity implements this interface so that the callback can be called by this fragment.
	 */
	public interface OnDoneWithSetupListener {
		
		/**
		 * Called when the user finishes with the setup fragment.  Passes relevant setup arguments.
		 * 
		 * @param deckSize Number of cards in the deck.
		 * @param delayPeriodMilliseconds Time to wait between placing cards into piles.
		 * @param isNew Whether to start a new shuffler or resume a previous one.
		 */
		public void onDoneWithSetup(int deckSize, int delayPeriodMilliseconds, boolean isNew);
	}
	
	OnDoneWithSetupListener mOnDoneWithSetupListener;
	
	private void setDeckSize(int deckSize, boolean setText) {
		mDeckSize = deckSize;
		setEstimatedTime(EstimateTime.estimatedTimeMinutes(mDeckSize, mDelayPeriodMillis), true);
		
		if (setText) {
			mEditTextDeckSize.setText(String.valueOf(mDeckSize));
		}
	}
	
	private void setSpeed(int speedCardsPerMinute, boolean setText) {
		mSpeedCardsPerMinute = speedCardsPerMinute;
		mDelayPeriodMillis = (int) periodMillisecondsFromSpeedPerMinute(mSpeedCardsPerMinute);
		setEstimatedTime(EstimateTime.estimatedTimeMinutes(mDeckSize, mDelayPeriodMillis), true);
		
		if (setText) {
			mEditTextCustomSpeed.setText(String.valueOf(mSpeedCardsPerMinute));
		}
	}
	
	private void setEstimatedTime(int estimatedTime, boolean setText) {
		mEstimatedTime = estimatedTime;
		
		if (setText) {
			mTextViewEstimatedTime.setText(String.valueOf(mEstimatedTime) + " min");
		}
	}
	
	@Override
	public void onAttach(Activity activity) {
		super.onAttach(activity);
		//Log.w(MainActivity.TAG_SETUP, "onAttach()");
		
		mActivity = activity;
		
		// Make sure that the parent activity implemented the callback.
		try {
			mOnDoneWithSetupListener = (OnDoneWithSetupListener) mActivity;
		} catch (ClassCastException ex) {
			BugSenseHandler.log(MainActivity.TAG_SETUP, ex);
			
			throw new ClassCastException(mActivity.toString() + "must implement OnDoneWithSetupListener");
		}
	}
	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		super.onCreateView(inflater, container, savedInstanceState);
		//Log.w(MainActivity.TAG_SETUP, "onCreateView()");
		
		mRes = getResources();
		mSharedPrefs = PreferenceManager.getDefaultSharedPreferences(mActivity);
		
		View view = inflater.inflate(R.layout.fragment_setup, container, false);
		
		/// Find and save views.
		mEditTextDeckSize = (EditText) view.findViewById(R.id.EditTextDeckSize);
		
		mTextViewSpeed = (TextView) view.findViewById(R.id.TextViewSpeed);
		mRadioButtonPresetSpeed = (RadioButton) view.findViewById(R.id.RadioButtonPresetSpeed);
		mRadioButtonsSpeed.add(mRadioButtonPresetSpeed);
		mSpinnerPresetSpeed = (Spinner) view.findViewById(R.id.SpinnerPresetSpeed);
		mRadioButtonCustomSpeed = (RadioButton) view.findViewById(R.id.RadioButtonCustomSpeed);
		mRadioButtonsSpeed.add(mRadioButtonCustomSpeed);
		mEditTextCustomSpeed = (EditText) view.findViewById(R.id.EditTextCustomSpeed);
		mTextViewEstimatedTime = (TextView) view.findViewById(R.id.TextViewEstimatedTime);
		
		mButtonResume = (Button) view.findViewById(R.id.ButtonResume);
		mButtonNew = (Button) view.findViewById(R.id.ButtonNew);
		///
		
		/// Populate the speed spinner.
		String speeds[] = mRes.getStringArray(R.array.speeds);
		ArrayAdapter<String> speedAdapter = new ArrayAdapter<String>(view.getContext(),
				R.layout.spinner_item_text, speeds);
		speedAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		mSpinnerPresetSpeed.setAdapter(speedAdapter);
		///
		
		/// Enable the resume button if there's a shuffler to resume.
		if (mSharedPrefs.getBoolean(ShufflerFragment.KEY_RESUMABLE, false)) {
			mButtonResume.setEnabled(true);
		} else {
			mButtonResume.setEnabled(false);
		}
		///
		
		/// Set listeners for everything.
		mEditTextDeckSize.addTextChangedListener(mTextWatcherEditTextDeckSize);
		mEditTextDeckSize.setOnEditorActionListener(mOnEditorActionEditText);
		mRadioButtonPresetSpeed.setOnCheckedChangeListener(mOnCheckedChangePresetSpeed);
		mSpinnerPresetSpeed.setOnItemSelectedListener(mOnItemSelectedSpinnerPresetSpeed);
		mRadioButtonCustomSpeed.setOnCheckedChangeListener(mOnCheckedChangeCustomSpeed);
		mEditTextCustomSpeed.addTextChangedListener(mTextWatcherEditTextCustomSpeed);
		mEditTextCustomSpeed.setOnEditorActionListener(mOnEditorActionEditText);
		mButtonResume.setOnClickListener(mOnClickButtonResume);
		mButtonNew.setOnClickListener(mOnClickButtonNew);
		///
		
		// XXX: Make sure this matches onSaveInstanceState().
		/// Restore previous or set default values.
		if (savedInstanceState != null) {
			setSpeed(savedInstanceState.getInt("mSpeedCardsPerMinute"), true);
			setDeckSize(savedInstanceState.getInt("mDeckSize"), true);
			mTextViewSpeedSaturation = savedInstanceState.getFloat("mTextViewSpeedSaturation");
		} else {
			setSpeed(0, true);
			setDeckSize(mSharedPrefs.getInt(KEY_SETTING_DECK_SIZE, 52), true);
			mTextViewSpeedSaturation = 1.0f;
		}
		
		int presetSpeed = mSharedPrefs.getInt(KEY_SETTING_PRESET_SPEED, 1);
		int customSpeed = MainActivity.numberFromString(mSharedPrefs.getString(KEY_SETTING_CUSTOM_SPEED, "60"), 0);
		boolean useCustomSpeed = mSharedPrefs.getBoolean(KEY_SETTING_USE_CUSTOM_SPEED, false);
		
		if (useCustomSpeed) {
			mRadioButtonPresetSpeed.setChecked(false);
			mRadioButtonCustomSpeed.setChecked(true);
			
			setSpeed(customSpeed, true);
		} else {
			mRadioButtonPresetSpeed.setChecked(true);
			mRadioButtonCustomSpeed.setChecked(false);
			
			updatePresetSpeedFromSpinner(mSpinnerPresetSpeed, presetSpeed);
		}
		
		mSpinnerPresetSpeed.setSelection(presetSpeed);
		mButtonNew.requestFocus();
		///
		
		return view;
	}
	
	@Override
	public void onStart() {
		super.onStart();
		//Log.w(MainActivity.TAG_SETUP, "onStart()");
		
		// Start the speed preview.
		mHandlerSpeedPreview.removeCallbacks(mTaskUpdateSpeedPreview);
		mHandlerSpeedPreview.post(mTaskUpdateSpeedPreview);
	}
	
	@Override
	public void onPause() {
		super.onPause();
		//Log.w(MainActivity.TAG_SETUP, "onPause()");
		
		mHandlerSpeedPreview.removeCallbacks(mTaskUpdateSpeedPreview);
	}
	
	@Override
	public void onStop() {
		super.onStop();
		//Log.w(MainActivity.TAG_SETUP, "onStop()");
		
		/// Save non-critical settings in onStop().
		SharedPreferences.Editor sharedPrefsEditor = mSharedPrefs.edit();
		
		sharedPrefsEditor.putInt(KEY_SETTING_DECK_SIZE, mDeckSize);
		sharedPrefsEditor.putInt(KEY_SETTING_PRESET_SPEED, mSpinnerPresetSpeed.getSelectedItemPosition());
		sharedPrefsEditor.putString(KEY_SETTING_CUSTOM_SPEED, mEditTextCustomSpeed.getText().toString());
		sharedPrefsEditor.putBoolean(KEY_SETTING_USE_CUSTOM_SPEED, mRadioButtonCustomSpeed.isChecked());
		
		sharedPrefsEditor.commit();
		///
	}
	
	@Override
	public void onSaveInstanceState(Bundle outState) {
		// XXX: There is a bug in the support library that can cause an IllegalStateException.
		// The workaround is to store a value before calling super.onSaveInstanceState().
		outState.putString("WORKAROUND_FOR_BUG_19917_KEY", "WORKAROUND_FOR_BUG_19917_VALUE");
		
		super.onSaveInstanceState(outState);
		//Log.w(MainActivity.TAG_SETUP, "onSaveInstanceState()");
		
		// XXX: Make sure this matches onCreateView()
		outState.putInt("mSpeedCardsPerMinute", mSpeedCardsPerMinute);
		outState.putInt("mDeckSize", mDeckSize);
		outState.putFloat("mTextViewSpeedSaturation", mTextViewSpeedSaturation);
	}
}