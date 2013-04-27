package com.andrewkeeton.divide.and.conquer.card.shuffler;

import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;

public class SettingsActivity extends PreferenceActivity implements OnSharedPreferenceChangeListener {
	
	public static final String KEY_PREF_SOUND_EFFECTS = "pref_sound_effects";
	public static final String KEY_PREF_TUTORIAL = "pref_tutorial";
	
	public static boolean mPrefSoundEffects = false;
	public static boolean mPrefTutorial = true;
	
	public SharedPreferences mSharedPreferences;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
		mSharedPreferences.registerOnSharedPreferenceChangeListener(this);
		
		addPreferencesFromResource(R.xml.preferences);
	}
	
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
		if (key.equals(KEY_PREF_SOUND_EFFECTS)) {
			SettingsActivity.mPrefSoundEffects = sharedPreferences.getBoolean(KEY_PREF_SOUND_EFFECTS, false);
		} else if (key.equals(KEY_PREF_TUTORIAL)) {
			SettingsActivity.mPrefTutorial = sharedPreferences.getBoolean(KEY_PREF_TUTORIAL, true);
		}
	}
	
	@Override
	public void onSaveInstanceState(Bundle outState) {
		// XXX: There is a bug in the support library that can cause an IllegalStateException.
		// The workaround is to store a value before calling super.onSaveInstanceState().
		outState.putString("WORKAROUND_FOR_BUG_19917_KEY", "WORKAROUND_FOR_BUG_19917_VALUE");
		
		super.onSaveInstanceState(outState);
	}
}