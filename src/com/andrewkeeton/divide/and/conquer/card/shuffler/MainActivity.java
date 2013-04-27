package com.andrewkeeton.divide.and.conquer.card.shuffler;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.Toast;

import com.andrewkeeton.divide.and.conquer.card.shuffler.billing.BillingService;
import com.andrewkeeton.divide.and.conquer.card.shuffler.billing.BillingService.RequestPurchase;
import com.andrewkeeton.divide.and.conquer.card.shuffler.billing.BillingService.RestoreTransactions;
import com.andrewkeeton.divide.and.conquer.card.shuffler.billing.Consts;
import com.andrewkeeton.divide.and.conquer.card.shuffler.billing.Consts.PurchaseState;
import com.andrewkeeton.divide.and.conquer.card.shuffler.billing.Consts.ResponseCode;
import com.andrewkeeton.divide.and.conquer.card.shuffler.billing.PurchaseObserver;
import com.andrewkeeton.divide.and.conquer.card.shuffler.billing.ResponseHandler;
import com.bugsense.trace.BugSenseHandler;

public class MainActivity extends FragmentActivity implements SetupFragment.OnDoneWithSetupListener {
	
	/// Debugging tags
	public static final String TAG_MAIN = "MAIN ACTIVITY";
	public static final String TAG_SETUP = "SETUP";
	public static final String TAG_SHUFFLER = "SHUFFLER";
	public static final String TAG_SETTINGS = "SETTINGS";
	public static final String TAG_MOVE = "MOVE";
	///
	
	public static final String SKU_DONATE_ONE_DOLLAR = "donate.one.dollar";
	
	public static final long DELAY_ANIMATION = (long) (1000.0 / 30.0);	// 30 fps
	
	/// In-app billing
	private Handler mHandler = new Handler();
	private BillingService mBillingService = new BillingService();
	private DCPurchaseObserver mDCPurchaseObserver;
	private boolean mInAppBillingSupported = false;
	///
	
	private Activity mActivity = this;
	
	/**
	 * A {@link PurchaseObserver} is used to get callbacks when Android Market sends
	 * messages to this application so that we can update the UI.
	 */	
	private class DCPurchaseObserver extends PurchaseObserver {
		public DCPurchaseObserver(Handler handler) {
			super(MainActivity.this, handler);
		}

		@Override
		public void onBillingSupported(boolean supported, String type) {
			if (Consts.DEBUG) {
				Log.i(TAG_MAIN, "supported: " + supported);
			}
			
			if (type.equals(Consts.ITEM_TYPE_INAPP)) {
				mInAppBillingSupported = supported;
			}
		}

		@Override
		public void onPurchaseStateChange(PurchaseState purchaseState, String itemId,
				int quantity, long purchaseTime, String developerPayload) {
			if (Consts.DEBUG) {
				Log.i(TAG_MAIN, "onPurchaseStateChange() itemId: " + itemId + " " + purchaseState);
			}

			if (purchaseState == PurchaseState.PURCHASED) {
				Toast.makeText(mActivity, "Thanks!", Toast.LENGTH_SHORT).show();
			}
		}

		@Override
		public void onRequestPurchaseResponse(RequestPurchase request, ResponseCode responseCode) {
			if (Consts.DEBUG) {
				Log.d(TAG_MAIN, request.mProductId + ": " + responseCode);
			}
			if (responseCode == ResponseCode.RESULT_OK) {
				if (Consts.DEBUG) {
					Log.i(TAG_MAIN, "purchase was successfully sent to server");
				}
			} else if (responseCode == ResponseCode.RESULT_USER_CANCELED) {
				if (Consts.DEBUG) {
					Log.i(TAG_MAIN, "user canceled purchase");
				}
			} else {
				if (Consts.DEBUG) {
					Log.i(TAG_MAIN, "purchase failed");
				}
			}
		}

		@Override
		public void onRestoreTransactionsResponse(RestoreTransactions request,
				ResponseCode responseCode) {
			if (responseCode == ResponseCode.RESULT_OK) {
				if (Consts.DEBUG) {
					Log.d(TAG_MAIN, "completed RestoreTransactions request");
				}
			} else {
				if (Consts.DEBUG) {
					Log.d(TAG_MAIN, "RestoreTransactions error: " + responseCode);
				}
			}
		}
	}
	
	private void donate() {
		// requestPurchase() will cause onRequestPurchaseResponse() and later onPurchaseStateChanged() to be called back.
		if (!mBillingService.requestPurchase(SKU_DONATE_ONE_DOLLAR, Consts.ITEM_TYPE_INAPP, null)) {
			Toast.makeText(this, "Sorry, donation failed :(", Toast.LENGTH_SHORT).show();
			BugSenseHandler.log("In-app donation failed", null);
		}
	}
	
	public static int numberFromString(String s, int defaultValue) {
		try {
			return Integer.valueOf(s);
		} catch (NumberFormatException ex) {
			return defaultValue;
		}
	}
	
	public static int numberFromEditText(EditText editText, int defaultValue) {
		return numberFromString(editText.getText().toString(), defaultValue);
	}
	
	public void onDoneWithSetup(int deckSize, int delayPeriodMilliseconds, boolean isNew) {
		//Log.w(TAG_MAIN, "onDoneWithSetup()");
		
		ShufflerFragment shufflerFragment =
				(ShufflerFragment) getSupportFragmentManager().findFragmentByTag(TAG_SHUFFLER);
		
		if (shufflerFragment == null) {
			// One-pane layout
			
			shufflerFragment = new ShufflerFragment();
			
			// Prepare arguments for the shuffler fragment.
			Bundle args = new Bundle();
			args.putInt(ShufflerFragment.ARG_DECK_SIZE, deckSize);
			args.putInt(ShufflerFragment.ARG_DELAY_PERIOD, delayPeriodMilliseconds);
			args.putBoolean(ShufflerFragment.ARG_IS_NEW, isNew);
			shufflerFragment.setArguments(args);
			
			// Move to the shuffler fragment and save this fragment to the back stack.
			FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
			transaction.replace(R.id.fragment_container, shufflerFragment, TAG_SHUFFLER);
			transaction.addToBackStack(null);
			
			transaction.commit();
		}
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		//Log.w(TAG_MAIN, "onCreate()");

		setContentView(R.layout.activity_main);
		
		/// Preferences
		PreferenceManager.setDefaultValues(this, R.xml.preferences, false);
		SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
		SettingsActivity.mPrefSoundEffects = sharedPrefs.getBoolean(SettingsActivity.KEY_PREF_SOUND_EFFECTS, false);
		SettingsActivity.mPrefTutorial = sharedPrefs.getBoolean(SettingsActivity.KEY_PREF_TUTORIAL, true);
		///
		
		// Catch all unhandled exceptions at bugsense.com.
		BugSenseHandler.setup(this, "30b9dd20");
		
		FragmentManager fm       = getSupportFragmentManager();
		Fragment        fragment = fm.findFragmentById(R.id.fragment_container);
		
		// If we are using activity_fragment_xml.xml then this the fragment will not be
		// null, otherwise it will be.
		if (fragment == null) {
			FragmentTransaction ft = fm.beginTransaction();
			ft.add(R.id.fragment_container, new SetupFragment(), TAG_SETUP);
			ft.commit();
		}
		
		/// In-app billing
		mDCPurchaseObserver = new DCPurchaseObserver(mHandler);
		mBillingService.setContext(this);
		
		// checkBillingSupported() will cause onBillingSupported() to be called back.
		mBillingService.checkBillingSupported(Consts.ITEM_TYPE_INAPP);
		///
	}
	
	public static void hideSoftKeyboard(Activity activity) {
		InputMethodManager inputMethodManager =
				(InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);

		View currentFocus = activity.getCurrentFocus();
		if (currentFocus != null) {
			inputMethodManager.hideSoftInputFromWindow(currentFocus.getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);
		}
	}
	
	@Override
	protected void onStart() {
		super.onStart();
		//Log.w(TAG_MAIN, "onStart()");
		ResponseHandler.register(mDCPurchaseObserver);
	}
	
	@Override
	protected void onResume() {
		super.onResume();
		//Log.w(TAG_MAIN, "onResume()");
	}
	
	@Override
	protected void onPause() {
		super.onPause();
		//Log.w(TAG_MAIN, "onPause()");
	}
	
	@Override
	protected void onStop() {
		super.onStop();
		//Log.w(TAG_MAIN, "onStop()");
		ResponseHandler.unregister(mDCPurchaseObserver);
	}
	
	@Override
	protected void onDestroy() {
		super.onDestroy();
		//Log.w(TAG_MAIN, "onDestroy()");
		mBillingService.unbind();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);
		//Log.w(TAG_MAIN, "onCreateOptionsMenu()");
		
		getMenuInflater().inflate(R.menu.menu_options, menu);
		
		return true;
	}
	
	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		super.onPrepareOptionsMenu(menu);
		
		MenuItem menuDonate = menu.findItem(R.id.menu_donate);
		if (mInAppBillingSupported) {
			menuDonate.setEnabled(true);
			menuDonate.setVisible(true);
		} else {
			menuDonate.setEnabled(false);
			menuDonate.setVisible(false);
		}
		
		return true;
	}
	
	@Override
	public boolean onMenuOpened(int featureId, Menu menu) {
		super.onMenuOpened(featureId, menu);
		//Log.w(TAG_MAIN, "onMenuOpened()");
		
		/// Pause the shuffler if it's running.
		FragmentManager fragmentManager = getSupportFragmentManager();
		ShufflerFragment fragmentShuffler = (ShufflerFragment)
				fragmentManager.findFragmentByTag(TAG_SHUFFLER);
		
		if (fragmentShuffler != null) {
			//Log.d(TAG_MAIN, "Pausing shuffler");
			fragmentShuffler.pauseShuffler();
		}
		///
		
		return true;
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		super.onOptionsItemSelected(item);
		//Log.w(TAG_MAIN, "onOptionsItemSelected()");
		
		switch (item.getItemId()) {
		case R.id.menu_settings:
			Intent intent = new Intent(this, SettingsActivity.class);
			startActivity(intent);
			
			return true;
			
		case R.id.menu_donate:
			donate();
			
			return true;
			
		default:
			return super.onOptionsItemSelected(item);
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