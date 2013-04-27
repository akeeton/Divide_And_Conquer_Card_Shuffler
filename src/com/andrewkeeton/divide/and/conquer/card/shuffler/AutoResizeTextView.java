package com.andrewkeeton.divide.and.conquer.card.shuffler;

import java.util.HashMap;

import android.content.Context;
import android.graphics.Rect;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.widget.TextView;

/**
 * Text view that auto adjusts text size to fit within the view. If the text
 * size equals the minimum text size and still does not fit, append with an
 * ellipsis.
 * 
 * @author Chase Colburn
 * @since Apr 4, 2011
 */
public class AutoResizeTextView extends TextView {

	// Minimum text size for this text view
	public static final float MIN_TEXT_SIZE = 20;

	// Interface for resize notifications
	public interface OnTextResizeListener {
		public void onTextResize(TextView textView, float oldSize, float newSize);
	}
	
	private HashMap<String, Float> mTextSizeCache = new HashMap<String, Float>();

	// Registered resize listener
	private OnTextResizeListener mTextResizeListener;

	// Flag for text and/or size changes to force a resize
	private boolean mNeedsResize = false;

	// Text size that is set from code. This acts as a starting point for
	// resizing
	private float mTextSize;

	// Temporary upper bounds on the starting text size
	private float mMaxTextSize = 0;

	// Lower bounds for text size
	private float mMinTextSize = MIN_TEXT_SIZE;

	// Default constructor override
	public AutoResizeTextView(Context context) {
		this(context, null);
	}

	// Default constructor when inflating from XML file
	public AutoResizeTextView(Context context, AttributeSet attrs) {
		this(context, attrs, 0);
	}

	// Default constructor override
	public AutoResizeTextView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		mTextSize = getTextSize();
	}

	/**
	 * When text changes, set the force resize flag to true and reset the text
	 * size.
	 */
	@Override
	protected void onTextChanged(final CharSequence text, final int start,
			final int before, final int after) {
		//Log.w("AutoResizeTextView", "onTextChanged() called: \"" + text + "\"");
		
		mNeedsResize = true;
		resizeText(text);
		mNeedsResize = false;
	}

	/**
	 * If the text view size changed, set the force resize flag to true
	 */
	@Override
	protected void onSizeChanged(int w, int h, int oldw, int oldh) {
		if (w != oldw || h != oldh) {
			mNeedsResize = true;
		}
	}

	/**
	 * Register listener to receive resize notifications
	 * 
	 * @param listener
	 */
	public void setOnResizeListener(OnTextResizeListener listener) {
		mTextResizeListener = listener;
	}

	/**
	 * Override the set text size to update our internal reference values
	 */
	@Override
	public void setTextSize(float size) {
		super.setTextSize(size);
		mTextSize = getTextSize();
	}

	/**
	 * Override the set text size to update our internal reference values
	 */
	@Override
	public void setTextSize(int unit, float size) {
		super.setTextSize(unit, size);
		mTextSize = getTextSize();
	}

	/**
	 * Set the upper text size limit and invalidate the view
	 * 
	 * @param maxTextSize
	 */
	public void setMaxTextSize(float maxTextSize) {
		mMaxTextSize = maxTextSize;
		requestLayout();
		invalidate();
	}

	/**
	 * Return upper text size limit
	 * 
	 * @return
	 */
	public float getMaxTextSize() {
		return mMaxTextSize;
	}

	/**
	 * Set the lower text size limit and invalidate the view
	 * 
	 * @param minTextSize
	 */
	public void setMinTextSize(float minTextSize) {
		mMinTextSize = minTextSize;
		requestLayout();
		invalidate();
	}

	/**
	 * Return lower text size limit
	 * 
	 * @return
	 */
	public float getMinTextSize() {
		return mMinTextSize;
	}

	/**
	 * Reset the text to the original size
	 */
	public void resetTextSize() {
		if (mTextSize > 0) {
			super.setTextSize(TypedValue.COMPLEX_UNIT_PX, mTextSize);
			mMaxTextSize = mTextSize;
		}
	}

	/**
	 * Resize text after measuring
	 */
	@Override
	protected void onLayout(boolean changed, int left, int top, int right,
			int bottom) {
		//Log.d("AutoResizeTextView", "onLayout() called");
		if (changed || mNeedsResize) {
			int widthLimit = (right - left) - getCompoundPaddingLeft()
					- getCompoundPaddingRight();
			int heightLimit = (bottom - top) - getCompoundPaddingBottom()
					- getCompoundPaddingTop();
			resizeText(this.getText(), widthLimit, heightLimit);
		}
		super.onLayout(changed, left, top, right, bottom);
	}

	/**
	 * Resize the text size with default width and height
	 */
	public void resizeText(CharSequence text) {
		int heightLimit = getHeight() - getPaddingBottom() - getPaddingTop();
		//Log.d("AutoResizeTextView", "heightLimit: " + heightLimit);
		int widthLimit = getWidth() - getPaddingLeft() - getPaddingRight();
		//Log.d("AutoResizeTextView", "widthLimit: " + widthLimit);
		resizeText(text, widthLimit, heightLimit);
	}

	/**
	 * Resize the text size with specified width and height
	 * 
	 * @param width
	 * @param height
	 */
	public void resizeText(CharSequence text, int width, int height) {
		// Do not resize if the view does not have dimensions or there is no
		// text
		if (text == null || text.length() == 0 || height <= 0 || width <= 0
				|| mTextSize == 0) {
			return;
		}
		
		float targetTextSize = mTextSize;
		TextPaint textPaint = getPaint();
		float oldTextSize = textPaint.getTextSize();
		
		// TODO: Encompass all stateful values in the cache lookup.
		if (mTextSizeCache.containsKey(text.toString())) {
			targetTextSize = mTextSizeCache.get(text.toString());
		} else {
			Rect bounds = new Rect();

			//Log.w("AutoResizeTextView", "Resizing text: \"" + this.getText() + "\"");

			/// Scale up
			while (true) {
				//Log.i("AutoResizeTextView", "targetTextSize: " + targetTextSize);

				getTextBounds(text, textPaint, targetTextSize, bounds);
				int textHeight = bounds.height();
				int textWidth = bounds.width();

				if (textHeight >= height || textWidth >= width) {
					break;
				}

				targetTextSize += 2.0f;
			}
			///

			/// Scale down
			while (true) {
				//Log.i("AutoResizeTextView", "targetTextSize: " + targetTextSize);

				getTextBounds(text, textPaint, targetTextSize, bounds);
				int textHeight = bounds.height();
				int textWidth = bounds.width();

				if (textHeight <= height && textWidth <= width) {
					break;
				}

				targetTextSize -= 2.0f;
			}
			///
		}
		
		mTextSizeCache.put(text.toString(), targetTextSize);
		
		// Leave some room for internal font padding.
		targetTextSize *= 0.85f;
		
		this.setTextSize(targetTextSize);

		// Notify the listener if registered
		if (mTextResizeListener != null) {
			mTextResizeListener.onTextResize(this, oldTextSize, targetTextSize);
		}

		// Reset force resize flag
		mNeedsResize = false;
	}
	
	private void getTextBounds(CharSequence text, TextPaint paint, float textSize, Rect bounds) {
		// Update the text paint object
		super.setTextSize(textSize);
		paint.getTextBounds(text.toString(), 0, text.length(), bounds);
		
		//Log.d("AutoResizeTextView", "textHeight: " + bounds.height());
		//Log.d("AutoResizeTextView", "textWidth: " + bounds.width());
	}
}