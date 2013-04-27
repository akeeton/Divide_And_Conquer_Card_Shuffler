package com.andrewkeeton.divide.and.conquer.card.shuffler;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertTrue;
import static junit.framework.Assert.fail;

import java.util.ArrayList;

import android.app.Activity;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnDismissListener;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.drawable.LevelListDrawable;
import android.graphics.drawable.TransitionDrawable;
import android.media.AudioManager;
import android.media.SoundPool;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

public class ShufflerFragment extends Fragment {
	
	public static final String ARG_DECK_SIZE = "ARG_DECK_SIZE";
	public static final String ARG_DELAY_PERIOD = "ARG_PERIOD";
	public static final String ARG_IS_NEW = "ARG_IS_NEW";
	public static final String KEY_RESUMABLE = "KEY_RESUMABLE";
	public static final int NUM_PILES = 8;
	public static final int MAX_COUNTDOWN = 3;
	public static final int DELAY_COUNTDOWN = 500;	// Time (in milliseconds) between numbers of the countdown.
	public static final int DELAY_PICKUP_ARROW = 1500 / 8;
	public static final int DELAY_DEFAULT = 750;
	public static final int TEXTVIEW_STATE_ALPHA_DELTA = 4;
	public static final int ALPHA_DECK = 255 / 4;
	
	Resources mRes;
	Activity mActivity;
	SharedPreferences mSharedPrefs;
	
	/// Views
	private View mViewRoot;
	
	private ImageView mImageViewArrowsArea;
	private Button mButtonShufflerControl;
	
	private int mImageViewPileIds[] = {
			R.id.ImageViewPile1, R.id.ImageViewPile2,
			R.id.ImageViewPile3, R.id.ImageViewPile4,
			R.id.ImageViewPile5, R.id.ImageViewPile6,
			R.id.ImageViewPile7, R.id.ImageViewPile8
	};
	
	private int mImageViewPickupArrowIds[] = {
			R.id.ImageViewPickupArrow1, R.id.ImageViewPickupArrow2,
			R.id.ImageViewPickupArrow3, R.id.ImageViewPickupArrow4,
			R.id.ImageViewPickupArrow5, R.id.ImageViewPickupArrow6,
			R.id.ImageViewPickupArrow7,
			R.id.ImageViewPickupArrow8Under, R.id.ImageViewPickupArrow8Above
	};
	
	private ImageView mImageViewDeck;
	private ViewTreeObserver mImageViewDeckViewTreeObserver;	// Used to resize mImageViewDeck if it's too small.
	private ImageView mImageViewPiles[];
	private ImageView mImageViewPickupArrows[];
	private ImageView mImageViewBottomArrowAbove;
	private ImageView mImageViewBottomArrowBelow;
	private ArrayList<ImageView> mImageViews = new ArrayList<ImageView>();
	ColorMatrixColorFilter mColorFilterDesaturated;
	
	private int mTextViewPileSizeIds[] = {
			R.id.TextViewPileSize1, R.id.TextViewPileSize2,
			R.id.TextViewPileSize3, R.id.TextViewPileSize4,
			R.id.TextViewPileSize5, R.id.TextViewPileSize6,
			R.id.TextViewPileSize7, R.id.TextViewPileSize8
	};
	
	private TextView mTextViewDeckSize;
	private TextView mTextViewPileSizes[];
	private TextView mTextViewBottomAmount;
	
	private AutoResizeTextView mTextViewState;
	
	private ProgressDialog mProgressDialogShuffling;
	///
	
	private Handler mHandler = new Handler();
	
	/// Sound
	private SoundPool mSoundPoolDealing;
	private ArrayList<Integer> mSoundDealingIds = new ArrayList<Integer>(NUM_PILES);
	///
	
	// TODO: Comments
	public enum State {
		READY,
		COUNTING_DOWN,
		DEALING,
		PICKING_UP,
		BOTTOMING,		// After pickup, a string of single-card piles need to be moved from the top to the bottom of the deck.
		PAUSED,
		DONE,
		INVALID
	}
	
	public State mState = State.INVALID;
	public State mStatePrev = State.INVALID;
	
	private int mDeckStartingSize = -1;
	private int mDeckSize = -1;
	private int mDelayPeriodMilliseconds = -1;
	private ArrayList<Move> mMoves = new ArrayList<Move>();
	
	private int mCountdown = -1;
	private int mPickupArrowNum = -1;
	private int mNumCardsToBottom = -1;
	private int mTextViewStateAlpha = -1;
	
	private boolean mFirstTimePickingUp = true;
	private boolean mFirstTimePickingUpNoDeck = true;
	private boolean mFirstTimeBottoming = true;
	
	private boolean mIsNew = true;
	private boolean mDoneShuffling = false;
	
	Thread mThreadShuffle = new Thread(new Runnable() {

		public void run() {
			long timeStart = System.currentTimeMillis();

			/// Construct the deck and shuffle it so we can get the moves.
			ArrayList<String> cardValues = new ArrayList<String>(mDeckStartingSize);

			for (int i = 0; i < mDeckStartingSize; i++) {
				cardValues.add("C" + i);
			}

			PileOfCards<String> deck = new PileOfCards<String>(cardValues, 0, mDeckStartingSize - 1);
			deck.shuffle();
			///

			// Fully sort the shuffled deck and get the moves required to sort it.
			mMoves = deck.sortDeckCompletely(NUM_PILES);

			/// If shuffling was quick, sleep enough so the progress dialog isn't a mysterious blip.
			long timeEnd = System.currentTimeMillis();
			long timeDuration = timeEnd - timeStart;
			final long TIME_DURATION_MIN = 1500;
			if (timeDuration < TIME_DURATION_MIN) {
				try {
					Thread.sleep(TIME_DURATION_MIN - timeDuration);
				} catch (InterruptedException ex) {
					// No big deal, do nothing.
				}
			}
			///

			// Stop the progress dialog.
			if (mProgressDialogShuffling != null) {
				mProgressDialogShuffling.dismiss();
				mProgressDialogShuffling = null;
			}

			mDoneShuffling = true;

			// Start the main task.
			mHandler.removeCallbacks(mTaskMain);
			mHandler.post(mTaskMain);
		}
	});
	
	Thread mThreadLoadSound = new Thread(new Runnable() {

		public void run() {
			// Set the hardware buttons to control the sound effects volume.
			mActivity.setVolumeControlStream(AudioManager.STREAM_MUSIC);
			mSoundPoolDealing = new SoundPool(10, AudioManager.STREAM_MUSIC, 0);

			mSoundDealingIds.add(mSoundPoolDealing.load(mActivity, R.raw.sound_deal1, 1));
			mSoundDealingIds.add(mSoundPoolDealing.load(mActivity, R.raw.sound_deal2, 1));
			mSoundDealingIds.add(mSoundPoolDealing.load(mActivity, R.raw.sound_deal3, 1));
			mSoundDealingIds.add(mSoundPoolDealing.load(mActivity, R.raw.sound_deal4, 1));
			mSoundDealingIds.add(mSoundPoolDealing.load(mActivity, R.raw.sound_deal5, 1));
			mSoundDealingIds.add(mSoundPoolDealing.load(mActivity, R.raw.sound_deal6, 1));
			mSoundDealingIds.add(mSoundPoolDealing.load(mActivity, R.raw.sound_deal7, 1));
			mSoundDealingIds.add(mSoundPoolDealing.load(mActivity, R.raw.sound_deal8, 1));
		}
	});
	
	private Runnable mTaskMain = new Runnable() {
		
		public void run() {
			
			LevelListDrawable levelListArrowsArea = (LevelListDrawable) mImageViewArrowsArea.getDrawable();
			
			switch (mState) {
			
			case READY:
				// Show instructions then wait for the user to press the start button.
				if (SettingsActivity.mPrefTutorial) {
					Dialog instructionsBegin = createDialogInstructions(R.string.title_instructions_begin,
							R.string.msg_instructions_begin, R.string.ok, null);
					instructionsBegin.show();
				}
				
				break;
				
			case COUNTING_DOWN:
				assertTrue("mCountdown > 0", mCountdown > 0);
				
				if (mCountdown == MAX_COUNTDOWN) {
					displayState(R.string.deal, true);
				}
				
				mImageViewArrowsArea.setImageLevel(
						mRes.getInteger(R.integer.arrows_countdown_3) + (MAX_COUNTDOWN - mCountdown));
				
				TransitionDrawable transitionCountdown = (TransitionDrawable) levelListArrowsArea.getCurrent();
				transitionCountdown.resetTransition();
				transitionCountdown.startTransition(DELAY_COUNTDOWN);
				
				mCountdown--;
				if (mCountdown == 0) {
					changeState(State.DEALING);
					mCountdown = MAX_COUNTDOWN;
				}
				
				// Run again soon.
				mHandler.removeCallbacks(this);
				mHandler.postDelayed(this, DELAY_COUNTDOWN);
				
				break;
				
			case DEALING:
				assertTrue("mMoves.size() > 0", mMoves.size() > 0);
				Move move = mMoves.remove(0);
				
				switch (move.mType) {
				case DEAL:
					int pileNum = move.mValue;
					assertTrue("pileNum >= 0", pileNum >= 0);
					assertTrue("pileNum < NUM_PILES", pileNum < NUM_PILES);
					
					/// The arrow gets a highlighted appearance then transitions to being unhighlighted.
					mImageViewArrowsArea.setImageLevel(pileNum);
					TransitionDrawable transitionArrow = (TransitionDrawable) levelListArrowsArea.getCurrent();

					transitionArrow.resetTransition();	// Begin at the highlighted appearance.
					transitionArrow.startTransition(mDelayPeriodMilliseconds);	// Begin switching to the unhighlighted appearance.
					///

					/// Update the deck size (and image if necessary).
					mDeckSize--;
					mTextViewDeckSize.setText(String.valueOf(mDeckSize));

					if (mDeckSize == 0) {
						mImageViewDeck.setImageLevel(mRes.getInteger(R.integer.pile_empty));
					}
					///

					/// The chosen pile gets a selected appearance then transitions to being unselected.
					ImageView imageViewPile = mImageViewPiles[pileNum];
					assertNotNull("imageViewPile", imageViewPile);

					imageViewPile.setImageLevel(mRes.getInteger(R.integer.pile_transition));
					LevelListDrawable levelListPile = (LevelListDrawable) imageViewPile.getDrawable();
					TransitionDrawable transitionPile = (TransitionDrawable) levelListPile.getCurrent();

					transitionPile.resetTransition();	// Begin at the selected appearance.
					transitionPile.startTransition(mDelayPeriodMilliseconds);	// Begin switching to the unselected appearance.
					///

					TextView textViewPileSize = mTextViewPileSizes[pileNum];
					assertNotNull("textViewPileSize", textViewPileSize);

					int pileSize = Integer.valueOf(textViewPileSize.getText().toString());
					textViewPileSize.setText(Integer.toString(pileSize + 1));

					/// Sound
					if (SettingsActivity.mPrefSoundEffects) {
						// TODO: Do this only once?
						/// Get the user volume settings.
						AudioManager audioManager = (AudioManager) mActivity.getSystemService(Activity.AUDIO_SERVICE);
						float actualVolume = (float) audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
						float maxVolume = (float) audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
						float volume = actualVolume / maxVolume;
						///

						// Play a card-dealing sound.
						if (mSoundPoolDealing != null) {
							if (pileNum < mSoundDealingIds.size()) {
								int soundId = mSoundDealingIds.get(pileNum);
								mSoundPoolDealing.play(soundId, volume, volume, 1, 0, 1.0f);
							}
						}
					}
					///
					
					// Wait before dealing the next card according to the user's preference.
					mHandler.removeCallbacks(this);
					mHandler.postDelayed(this, mDelayPeriodMilliseconds);
					
					break;
					
				case PICKUP:
					changeState(State.PICKING_UP);
					
					mImageViewArrowsArea.setImageLevel(mRes.getInteger(R.integer.arrows_nothing));
					mButtonShufflerControl.setText(R.string.Continue);
					
					if (SettingsActivity.mPrefTutorial && (mFirstTimePickingUp || mFirstTimePickingUpNoDeck)) {
						int messageId;
						
						if (mFirstTimePickingUpNoDeck) {
							messageId = R.string.msg_instructions_pickup_no_deck;
							mFirstTimePickingUpNoDeck = false;
						} else {	// mFirstTimePickingUp
							messageId = R.string.msg_instructions_pickup;
							mFirstTimePickingUp = false;
						}
						
						// Show the pickup instructions and delay the animation until the user presses OK.
						Dialog instructionsPickup = createDialogInstructions(R.string.title_instructions_pickup, messageId,
								R.string.ok, new OnDismissListener() {
							
									public void onDismiss(DialogInterface dialog) {
										// Wait until the dialog is dismissed to continue with the PICKING_UP state.
										mHandler.removeCallbacks(mTaskMain);
										mHandler.post(mTaskMain);
									}
						});
						
						instructionsPickup.show();
					} else {
						// No instructions, just go straight to picking up.
						mHandler.removeCallbacks(this);
						mHandler.post(this);
					}
					
					break;
					
				case BOTTOM:
					changeState(State.BOTTOMING);
					
					mNumCardsToBottom = move.mValue;
					
					mImageViewArrowsArea.setImageLevel(mRes.getInteger(R.integer.arrows_nothing));
					mButtonShufflerControl.setText(R.string.Continue);
					
					// Only show an instructional dialog if the user preference is checked and it's the first bottom.
					if (SettingsActivity.mPrefTutorial && mFirstTimeBottoming) {
						mFirstTimeBottoming = false;
					
						String title = mRes.getString(R.string.title_instructions_bottom);
						String msg = mRes.getQuantityString(R.plurals.msg_instructions_bottom,
								mNumCardsToBottom, mNumCardsToBottom);
						String buttonText = mRes.getString(R.string.ok);
						OnDismissListener onDismiss = new OnDismissListener() {
							
							public void onDismiss(DialogInterface dialog) {
								// Wait until the dialog is dismissed to continue with the BOTTOMING state.
								mHandler.removeCallbacks(mTaskMain);
								mHandler.post(mTaskMain);
							}
						};
						
						Dialog instructionsBottom = createDialogInstructions(title, msg, buttonText, onDismiss);
						instructionsBottom.show();
					} else {
						mHandler.removeCallbacks(this);
						mHandler.post(this);
					}
					
					break;
					
				case NONE:
				default:
					fail();
					break;
				} /* switch (move.mType) */
				
				break;
				
			case PICKING_UP:
				if (mPickupArrowNum == 0) {
					displayState(R.string.pick_up, true);
				}
				
				if (mPickupArrowNum == NUM_PILES - 1) {
					// Last arrow
					
					// The last arrow depends on whether there is a deck left or not.  Either go above or under.
					if (mDeckSize > 0) {
						mImageViewDeck.setAlpha(ALPHA_DECK);
						mImageViewPickupArrows[NUM_PILES - 1].setVisibility(View.VISIBLE);	// Under
					} else {
						mImageViewPickupArrows[NUM_PILES].setVisibility(View.VISIBLE);		// Above
					}
					
					// Stop once all arrows have been shown.
					mHandler.removeCallbacks(this);
					
				} else {
					// Show one arrow at a time.
					mImageViewPickupArrows[mPickupArrowNum].setVisibility(View.VISIBLE);
					
					// Go to the next arrow in a bit.
					mPickupArrowNum++;
					mHandler.removeCallbacks(this);
					mHandler.postDelayed(this, DELAY_PICKUP_ARROW);
				}
				
				break;
				
			case BOTTOMING:
					assertTrue("mNumCardsToBottom > 0", mNumCardsToBottom > 0);
					
					displayState(R.string.bottom, true);
					
					// Tell the user to send some number of cards to the bottom.
					mImageViewBottomArrowAbove.setVisibility(View.VISIBLE);
					mImageViewBottomArrowBelow.setVisibility(View.VISIBLE);
					mTextViewBottomAmount.setVisibility(View.VISIBLE);
					mTextViewBottomAmount.setText(String.valueOf(mNumCardsToBottom));
					mImageViewDeck.setAlpha(ALPHA_DECK);
					
					// We're done once we've run out of moves...
					if (mMoves.isEmpty()) {
						doneShuffling();
					}
					
				break;
				
			case PAUSED:
				break;
				
			case INVALID:
				fail();
				break;
				
			default:
				fail();
				break;
			}
		}
	};
	
	private Runnable mTaskTextViewStateFade = new Runnable() {
		
		public void run() {
			if (mTextViewStateAlpha > 200) {
				mTextViewStateAlpha -= TEXTVIEW_STATE_ALPHA_DELTA;
			} else {
				mTextViewStateAlpha -= 3 * TEXTVIEW_STATE_ALPHA_DELTA;
			}
			
			if (mTextViewStateAlpha <= 0) {
				mTextViewState.setVisibility(View.INVISIBLE);
				mHandler.removeCallbacks(this);
			} else {
				ColorStateList colors = mTextViewState.getTextColors();
				mTextViewState.setTextColor(colors.withAlpha(mTextViewStateAlpha));
				
				mHandler.removeCallbacks(this);
				mHandler.postDelayed(this, MainActivity.DELAY_ANIMATION);
			}
		}
	};
	
	private OnClickListener mOnClickButtonShufflerControl = new OnClickListener() {
		
		public void onClick(View v) {
			switch (mState) {
			
			case PICKING_UP:
				
				mPickupArrowNum = 0;
				
				/// Reset the screen once everything is picked up.
				mImageViewDeck.setAlpha(255);
				
				for (int pileNum = 0; pileNum < NUM_PILES; pileNum++) {
					mImageViewPiles[pileNum].setImageLevel(mRes.getInteger(R.integer.pile_empty));
					mTextViewPileSizes[pileNum].setText(Integer.toString(0));
				}
				
				for (int i = 0; i < mImageViewPickupArrows.length; i++) {
					mImageViewPickupArrows[i].setVisibility(View.INVISIBLE);
				}
				///
				
				/// Reset the deck count and image.
				mDeckSize = mDeckStartingSize;
				mTextViewDeckSize.setText(String.valueOf(mDeckSize));
				mImageViewDeck.setImageLevel(mRes.getInteger(R.integer.pile_not_empty));
				///
				
				// We're done once we've run out of moves...
				if (mMoves.isEmpty()) {
					doneShuffling();
					
					return;
				}
				
				// If the next move is a bottom, then resume dealing immediately so that the bottoming begins.
				// Otherwise, resume shuffling via a countdown.
				if (mMoves.get(0).mType == Move.MoveType.BOTTOM) {
					resumeShuffler(State.DEALING, 0);
				} else {
					resumeShuffler(State.COUNTING_DOWN, DELAY_DEFAULT);
				}
				
				break;
				
			case BOTTOMING:
				mImageViewBottomArrowAbove.setVisibility(View.INVISIBLE);
				mImageViewBottomArrowBelow.setVisibility(View.INVISIBLE);
				mTextViewBottomAmount.setVisibility(View.INVISIBLE);
				mImageViewDeck.setAlpha(255);
				
				resumeShuffler(State.COUNTING_DOWN, DELAY_DEFAULT);
				
				break;
				
			case READY:
				resumeShuffler(State.COUNTING_DOWN, DELAY_DEFAULT);
				
				break;
				
			case PAUSED:
				if (mStatePrev == State.DEALING) {
					resumeShuffler(State.COUNTING_DOWN, DELAY_DEFAULT);
				} else {
					resumeShuffler(mStatePrev, DELAY_DEFAULT);
				}
				
				break;
				
			case COUNTING_DOWN:
			case DEALING:
				pauseShuffler();
				
				break;
				
			default:
				fail();
				break;
			}
		}
	};
	
	public void resumeShuffler(State resumeState, int delayMilliseconds) {
		if (resumeState == State.COUNTING_DOWN || resumeState == State.DEALING) {
			mButtonShufflerControl.setText(R.string.Pause);
		} else {
			mButtonShufflerControl.setText(R.string.Continue);
		}
		
		mImageViewArrowsArea.setImageLevel(mRes.getInteger(R.integer.arrows_nothing));
		mTextViewState.setVisibility(View.INVISIBLE);
		
		// Re-saturate all of the images.
		for (ImageView imageView : mImageViews) {
			imageView.clearColorFilter();
		}
		
		changeState(resumeState);
		
		mHandler.removeCallbacks(mTaskMain);
		mHandler.postDelayed(mTaskMain, delayMilliseconds);
	}
	
	public void pauseShuffler() {
		if (mState == State.DONE) {
			return;
		}
		
		mButtonShufflerControl.setText(R.string.Resume);
		displayState(R.string.paused, false);
		
		// Desaturate all of the images.
		for (ImageView imageView : mImageViews) {
			imageView.setColorFilter(mColorFilterDesaturated);
		}
		
		changeState(State.PAUSED);
		mCountdown = MAX_COUNTDOWN;

		mHandler.removeCallbacks(mTaskMain);
	}
	
	private void doneShuffling() {
		changeState(State.DONE);
		mButtonShufflerControl.setText(R.string.Done);
		mButtonShufflerControl.setEnabled(false);
		displayState(R.string.done, true);
		
		mHandler.removeCallbacks(mTaskMain);
	}
	
	public void changeState(State newState) {
		if (newState != mState) {
			mStatePrev = mState;
			mState = newState;
		}
		
		assertTrue("mStatePrev != mState", mStatePrev != mState);
		
		//Log.d(MainActivity.TAG_SHUFFLER, "state " + mStatePrev + " -> state " + mState);
	}
	
	private void displayState(int stateStringId, boolean fade) {
		mTextViewState.setText(mRes.getString(stateStringId).toLowerCase());
		mTextViewState.setVisibility(View.VISIBLE);
		mTextViewStateAlpha = 255;
		mTextViewState.setTextColor(mTextViewState.getTextColors().withAlpha(mTextViewStateAlpha));
		
		mHandler.removeCallbacks(mTaskTextViewStateFade);
		
		if (fade) {
			mHandler.postDelayed(mTaskTextViewStateFade, MainActivity.DELAY_ANIMATION);
		}
	}
	
	private Dialog mDialogInstructions = null;
	
	private OnClickListener mOnClickButtonDialogInstructionsOK = new OnClickListener() {

		public void onClick(View v) {
			mDialogInstructions.dismiss();
		}
	};
	
	/**
	 * Creates and returns a custom Dialog that contains large instructions and a large OK button.
	 * 
	 * @param titleId Resource ID of the dialog title string.
	 * @param msgId Resource ID of the message string.
	 * @param buttonTextId Resource ID of the button text.
	 * @param onDismiss Listener for when the dialog is dismissed.
	 * @return The custom instructions Dialog.
	 */
	private Dialog createDialogInstructions(int titleId, int msgId, int buttonTextId, OnDismissListener onDismiss) {
		String title = mRes.getString(titleId);
		String msg = mRes.getString(msgId);
		String buttonText = mRes.getString(buttonTextId);
		
		return createDialogInstructions(title, msg, buttonText, onDismiss);
	}
	
	private Dialog createDialogInstructions(String title, String msg, String buttonText, OnDismissListener onDismiss) {
		// Use the custom instruction layout for the dialog.
		LayoutInflater inflater = (LayoutInflater) mActivity.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		LinearLayout layoutInstructions =
				(LinearLayout) inflater.inflate(R.layout.linearlayout_instructions, null, false);
		
		TextView textViewMessage = (TextView) layoutInstructions.findViewById(R.id.TextViewDialogInstructions);
		Button buttonOK = (Button) layoutInstructions.findViewById(R.id.ButtonDialogInstructionsOK);
		
		Dialog dialog = new Dialog(mActivity);
		dialog.getWindow().setBackgroundDrawableResource(R.color.background_dialog);
		dialog.setContentView(layoutInstructions);
		dialog.setTitle(title);
		dialog.setOnDismissListener(onDismiss);
		textViewMessage.setText(msg);
		buttonOK.setText(buttonText);
		buttonOK.setOnClickListener(mOnClickButtonDialogInstructionsOK);
		
		mDialogInstructions = dialog;
		
		return mDialogInstructions;
	}
	
	@Override
	public void onAttach(Activity activity) {
		super.onAttach(activity);
		//Log.w(MainActivity.TAG_SHUFFLER, "onAttach()");
		
		mActivity = activity;
		
		// Force the screen into portrait mode so that placing it down on a table won't cause sporadic orientation changes.
		activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
		
		// Force the screen to remain on.
		activity.getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
	}
	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		super.onCreateView(inflater, container, savedInstanceState);
		//Log.w(MainActivity.TAG_SHUFFLER, "onCreateView()");
		
		mRes = getResources();
		mSharedPrefs = PreferenceManager.getDefaultSharedPreferences(mActivity);
		
		mViewRoot = inflater.inflate(R.layout.fragment_shuffler, container, false);
		
		/// Find/create and store views.
		mImageViewArrowsArea = (ImageView) mViewRoot.findViewById(R.id.ImageViewArrowsArea);
		mImageViews.add(mImageViewArrowsArea);
		mButtonShufflerControl = (Button) mViewRoot.findViewById(R.id.ButtonShufflerControl);
		mImageViewDeck = (ImageView) mViewRoot.findViewById(R.id.ImageViewDeck);
		mImageViews.add(mImageViewDeck);
		mTextViewDeckSize = (TextView) mViewRoot.findViewById(R.id.TextViewDeckSize);
		
		mImageViewPiles = new ImageView[mImageViewPileIds.length];
		mTextViewPileSizes = new TextView[mTextViewPileSizeIds.length];
		mImageViewPickupArrows = new ImageView[mImageViewPickupArrowIds.length];
		
		for (int i = 0; i < mImageViewPileIds.length; i++) {
			mImageViewPiles[i] = (ImageView) mViewRoot.findViewById(mImageViewPileIds[i]);
			mImageViews.add(mImageViewPiles[i]);
		}
			
		for (int i = 0; i < mTextViewPileSizeIds.length; i++) {
			mTextViewPileSizes[i] = (TextView) mViewRoot.findViewById(mTextViewPileSizeIds[i]);
			
		}
		
		for (int i = 0; i < mImageViewPickupArrowIds.length; i++) {
			mImageViewPickupArrows[i] = (ImageView) mViewRoot.findViewById(mImageViewPickupArrowIds[i]);
			mImageViews.add(mImageViewPickupArrows[i]);
		}
		
		mImageViewBottomArrowAbove = (ImageView) mViewRoot.findViewById(R.id.ImageViewBottomArrowAbove);
		mImageViews.add(mImageViewBottomArrowAbove);
		mImageViewBottomArrowBelow = (ImageView) mViewRoot.findViewById(R.id.ImageViewBottomArrowBelow);
		mImageViews.add(mImageViewBottomArrowBelow);
		
		mTextViewBottomAmount = (TextView) mViewRoot.findViewById(R.id.TextViewBottomAmount);
		
		mTextViewState = (AutoResizeTextView) mViewRoot.findViewById(R.id.TextViewState);
		///
		
		/// Add listeners to views.
		mButtonShufflerControl.setOnClickListener(mOnClickButtonShufflerControl);
		///
		
		ColorMatrix colorMatrix = new ColorMatrix();
		colorMatrix.setSaturation(0);
		mColorFilterDesaturated = new ColorMatrixColorFilter(colorMatrix);
		
		/// Sound
		// Put the sound loading into another thread so it doesn't clog the UI thread.
		// If the thread doesn't finish in time, then an unloaded sound won't be played.
		mThreadLoadSound.run();
		///
		
		Bundle args = this.getArguments();
		assertNotNull("args", args);
		mIsNew = args.getBoolean(ARG_IS_NEW);
		args.putBoolean(KEY_RESUMABLE, false);
		
		if (!mIsNew) {
			// We should be resuming from a paused state.
			
			/// XXX: Make sure this matches onDestroy().
			mState = State.values()[mSharedPrefs.getInt("mStateOrdinal", -1)];
			assertEquals("mState == State.PAUSED", State.PAUSED, mState);
			mStatePrev = State.values()[mSharedPrefs.getInt("mStatePrevOrdinal", -1)];
			
			mDeckStartingSize = mSharedPrefs.getInt("mDeckStartingSize", -1);
			mDeckSize = mSharedPrefs.getInt("mDeckSize", -1);
			mDelayPeriodMilliseconds = mSharedPrefs.getInt("mDelayPeriodMilliseconds", -1);
			mMoves = Move.arrayListFromString(mSharedPrefs.getString("mMoves", "[E0]"));
			mCountdown = mSharedPrefs.getInt("mCountdown", -1);
			mPickupArrowNum = mSharedPrefs.getInt("mPickupArrowNum", -1);
			mNumCardsToBottom = mSharedPrefs.getInt("mNumCardsToBottom", -1);
			mTextViewStateAlpha = mSharedPrefs.getInt("mTextViewStateAlpha", -1);
			mFirstTimePickingUp = mSharedPrefs.getBoolean("mFirstTimePickingUp", false);
			mFirstTimePickingUpNoDeck = mSharedPrefs.getBoolean("mFirstTimePickingUpNoDeck", false);
			mFirstTimeBottoming = mSharedPrefs.getBoolean("mFirstTimeBottoming", false);
			mDoneShuffling = mSharedPrefs.getBoolean("mDoneShuffling", false);
			
			for (int i = 0; i < mTextViewPileSizes.length; i++) {
				String strTextViewPileSize = "mTextViewPileSizes" + i;
				TextView textViewPileSize = mTextViewPileSizes[i];
				
				String text = mSharedPrefs.getString(strTextViewPileSize + "Text", "ERR");
				textViewPileSize.setText(text);
				
				if (Integer.valueOf(text) > 0) {
					mImageViewPiles[i].setImageLevel(mRes.getInteger(R.integer.pile_not_empty));
				} else {
					mImageViewPiles[i].setImageLevel(mRes.getInteger(R.integer.pile_empty));
				}
			}
			
			mTextViewDeckSize.setText(String.valueOf(mDeckSize));
			if (mDeckSize > 0) {
				mImageViewDeck.setImageLevel(mRes.getInteger(R.integer.pile_not_empty));
			} else {
				mImageViewDeck.setImageLevel(mRes.getInteger(R.integer.pile_empty));
			}
			
			for (int i = 0; i < mImageViewPickupArrows.length; i++) {
				String strImageViewPickupArrow = "mImageViewPickupArrows" + i;
				ImageView pickupArrow = mImageViewPickupArrows[i];

				int visibility = mSharedPrefs.getInt(strImageViewPickupArrow + "Visibility", -1);
				pickupArrow.setVisibility(visibility);
			}
			
			mImageViewBottomArrowAbove.setVisibility(mSharedPrefs.getInt("mImageViewBottomArrowAboveVisibility", -1));
			mImageViewBottomArrowBelow.setVisibility(mSharedPrefs.getInt("mImageViewBottomArrowAboveVisibility", -1));
			mTextViewBottomAmount.setVisibility(mSharedPrefs.getInt("mTextViewBottomAmountVisibility", -1));
			mTextViewBottomAmount.setText(String.valueOf(mNumCardsToBottom));
			
			mTextViewState.setVisibility(mSharedPrefs.getInt("mTextViewStateVisibility", -1));
			mTextViewState.setText(mSharedPrefs.getString("mTextViewStateText", "ERR"));
			
			mImageViewArrowsArea.setImageLevel(mSharedPrefs.getInt("mImageViewArrowsAreaLevel", -1));
			
			// If there's an arrow pointing below the deck, then the deck is translucent.
			if (mImageViewPickupArrows[NUM_PILES - 1].getVisibility() == View.VISIBLE
					|| mImageViewBottomArrowAbove.getVisibility() == View.VISIBLE) {
				mImageViewDeck.setAlpha(ALPHA_DECK);
			}
			
			// "Pause" the shuffler so it looks desaturated.
			mState = mStatePrev;
			pauseShuffler();
			///
			
			// Start the main task.
			mHandler.removeCallbacks(mTaskMain);
			mHandler.post(mTaskMain);
		
		} else {
			// Starting a new shuffler instance, so set default values.
			
			mState = State.READY;
			mStatePrev = State.INVALID;
			mDeckStartingSize = args.getInt(ARG_DECK_SIZE);
			mDeckSize = mDeckStartingSize;
			mDelayPeriodMilliseconds = args.getInt(ARG_DELAY_PERIOD);
			mCountdown = MAX_COUNTDOWN;
			mPickupArrowNum = 0;
			mNumCardsToBottom = 0;
			mTextViewStateAlpha = 0;
			mFirstTimePickingUp = true;
			mFirstTimePickingUpNoDeck = true;
			mFirstTimeBottoming = true;
			mDoneShuffling = false;
			
			mImageViewArrowsArea.setImageLevel(mRes.getInteger(R.integer.arrows_nothing));
			mImageViewDeck.setImageLevel(mRes.getInteger(R.integer.pile_not_empty));
			mTextViewDeckSize.setText(String.valueOf(mDeckStartingSize));
			for (int i = 0; i < mImageViewPickupArrows.length; i++) {
				mImageViewPickupArrows[i].setVisibility(View.INVISIBLE);
			}
			mImageViewBottomArrowAbove.setVisibility(View.INVISIBLE);
			mImageViewBottomArrowBelow.setVisibility(View.INVISIBLE);
			mTextViewBottomAmount.setVisibility(View.INVISIBLE);
			
			mTextViewState.setVisibility(View.INVISIBLE);
			
		}
		
		// Shuffle if A) the deck was never shuffled or B) shuffling was interrupted.
		if (!mDoneShuffling) {
			/// Put the deck construction and shuffling into a separate thread.
			mProgressDialogShuffling = ProgressDialog.show(mActivity,
					"", mRes.getString(R.string.Shufflingellipsis), false);
			
			mThreadShuffle.start();
			///
		}
		
		return mViewRoot;
	}
	
	public boolean mNeedsResizing = true;
	@Override
	public void onStart() {
		super.onStart();
		//Log.w(MainActivity.TAG_SHUFFLER, "onStart()");
		
		/// Resize other ImageViews if mImageViewDeck is squashed.
		mImageViewDeckViewTreeObserver = mImageViewDeck.getViewTreeObserver();
		mImageViewDeckViewTreeObserver.addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
			
			public boolean onPreDraw() {
				if (!mNeedsResizing) {
					return true;
				}
				
				int heightWanted = mImageViewDeck.getDrawable().getIntrinsicHeight();
				int height = mImageViewDeck.getMeasuredHeight();
				int heightDiff = heightWanted - height;
				int heightArrowsArea = mImageViewArrowsArea.getMeasuredHeight();
				int heightArrowsAreaFixed = heightArrowsArea - heightDiff;
				assertTrue("heightDiff >= 0", heightDiff >= 0);
				
				mImageViewArrowsArea.setMaxHeight(heightArrowsAreaFixed);
				mViewRoot.requestLayout();
				
				mNeedsResizing = false;
				
				//Log.d(MainActivity.TAG_SHUFFLER, "heightWanted: " + heightWanted);
				//Log.d(MainActivity.TAG_SHUFFLER, "heightArrowsArea: " + heightArrowsArea);
				//Log.d(MainActivity.TAG_SHUFFLER, "height: " + height);
				//Log.d(MainActivity.TAG_SHUFFLER, "heightDiff: " + heightDiff);
				//Log.d(MainActivity.TAG_SHUFFLER, "heightArrowsAreaFixed: " + heightArrowsAreaFixed);
				
				return true;
			}
		});
		///
	}
	
	@Override
	public void onResume() {
		super.onResume();
		//Log.w(MainActivity.TAG_SHUFFLER, "onResume()");
	}
	
	@Override
	public void onPause() {
		super.onPause();
		//Log.w(MainActivity.TAG_SHUFFLER, "onPause()");
		
		/// Wait for the non-UI threads to finish before we go anywhere.
		if (mThreadShuffle.isAlive()) {
			try {
				mThreadShuffle.join();
			} catch (InterruptedException ex) {
				// Only if we call interrupt().
			}
		}
		
		if (mThreadLoadSound.isAlive()) {
			try {
				mThreadLoadSound.join();
			} catch (InterruptedException ex) {
				// Only if we call interrupt().
			}
		}
		///
		
		pauseShuffler();
	}
	
	@Override
	public void onStop() {
		super.onStop();
		//Log.w(MainActivity.TAG_SHUFFLER, "onStop()");
		
		// Unlock the screen orientation.
		mActivity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);

		// Allow the screen to turn off.
		mActivity.getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
	}
	
	@Override
	public void onDestroy() {
		super.onDestroy();
		//Log.w(MainActivity.TAG_SHUFFLER, "onDestroy()");
		
		mProgressDialogShuffling = null;
		
		SharedPreferences.Editor sharedPrefsEditor = mSharedPrefs.edit();
		if (mState == State.DONE) {
			//Log.d(MainActivity.TAG_SHUFFLER, "Storing false for KEY_RESUMABLE");
			// This shuffler instance is no longer resumable if we're done.
			sharedPrefsEditor.putBoolean(KEY_RESUMABLE, false);
		} else {
			// Save the fragment's state to shared preferences (instead of bundled in onSaveInstanceState())
			// so it can be resumed even after the app has been killed.
			
			/// XXX: Make sure this matches onCreateView().
			sharedPrefsEditor.putInt("mStateOrdinal", mState.ordinal());
			sharedPrefsEditor.putInt("mStatePrevOrdinal", mStatePrev.ordinal());
			sharedPrefsEditor.putString("mMoves", mMoves.toString());
			sharedPrefsEditor.putInt("mDeckStartingSize", mDeckStartingSize);
			sharedPrefsEditor.putInt("mDeckSize", mDeckSize);
			sharedPrefsEditor.putInt("mDelayPeriodMilliseconds", mDelayPeriodMilliseconds);
			sharedPrefsEditor.putInt("mCountdown", mCountdown);
			sharedPrefsEditor.putInt("mPickupArrowNum", mPickupArrowNum);
			sharedPrefsEditor.putInt("mNumCardsToBottom", mNumCardsToBottom);
			sharedPrefsEditor.putInt("mTextViewStateAlpha", mTextViewStateAlpha);
			sharedPrefsEditor.putBoolean("mFirstTimePickingUp", mFirstTimePickingUp);
			sharedPrefsEditor.putBoolean("mFirstTimePickingUpNoDeck", mFirstTimePickingUpNoDeck);
			sharedPrefsEditor.putBoolean("mFirstTimeBottoming", mFirstTimeBottoming);
			sharedPrefsEditor.putBoolean("mDoneShuffling", mDoneShuffling);
			
			for (int i = 0; i < mTextViewPileSizes.length; i++) {
				String strTextViewPileSize = "mTextViewPileSizes" + i;
				TextView textViewPileSize = mTextViewPileSizes[i];
				
				sharedPrefsEditor.putString(strTextViewPileSize + "Text", textViewPileSize.getText().toString());
			}
			
			for (int i = 0; i < mImageViewPickupArrows.length; i++) {
				String strImageViewPickupArrow = "mImageViewPickupArrows" + i;
				ImageView pickupArrow = mImageViewPickupArrows[i];
				
				sharedPrefsEditor.putInt(strImageViewPickupArrow + "Visibility", pickupArrow.getVisibility());
			}
			
			sharedPrefsEditor.putInt("mImageViewBottomArrowAboveVisibility", mImageViewBottomArrowAbove.getVisibility());
			sharedPrefsEditor.putInt("mTextViewBottomAmountVisibility", mTextViewBottomAmount.getVisibility());
			
			sharedPrefsEditor.putInt("mTextViewStateVisibility", mTextViewState.getVisibility());
			
			sharedPrefsEditor.putInt("mImageViewArrowsAreaLevel", mImageViewArrowsArea.getDrawable().getLevel());
			
			// Signal that the shuffler can be resumed.
			sharedPrefsEditor.putBoolean(KEY_RESUMABLE, true);
			///
		}
		
		sharedPrefsEditor.commit();
	}
	
	@Override
	public void onSaveInstanceState(Bundle outState) {
		// XXX: There is a bug in the support library that can cause an IllegalStateException.
		// The workaround is to store a value before calling super.onSaveInstanceState().
		outState.putString("WORKAROUND_FOR_BUG_19917_KEY", "WORKAROUND_FOR_BUG_19917_VALUE");
		
		super.onSaveInstanceState(outState);
		//Log.w(MainActivity.TAG_SHUFFLER, "onSaveInstanceState()");
	}
}