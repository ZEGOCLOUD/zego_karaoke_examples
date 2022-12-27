/*
 * Copyright (C) 2017 wangchenyan
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package com.zegocloud.lrcview;

import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Looper;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.Scroller;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


@SuppressLint("StaticFieldLeak")
public class LrcView extends View {
    private static final String TAG = "LrcView";
    private static final long ADJUST_DURATION = 100;
    private static final long TIMELINE_KEEP_TIME = 4 * DateUtils.SECOND_IN_MILLIS;

    private List<LrcEntry> mLrcEntryList = new ArrayList<>();
    private TextPaint mLrcPaint = new TextPaint();
    private TextPaint mTimePaint = new TextPaint();
    private Paint.FontMetrics mTimeFontMetrics;
    private Drawable mPlayDrawable;
    private float mDividerHeight;
    private long mAnimationDuration;
    private int mNormalTextColor;
    private float mNormalTextSize;
    private int mCurrentTextColor;
    private float mCurrentTextSize;
    private int mTimelineTextColor;
    private int mTimelineColor;
    private int mTimeTextColor;
    private int mDrawableWidth;
    private int mTimeTextWidth;
    private String mDefaultLabel;
    private float mLrcPadding;
    private OnPlayClickListener mOnPlayClickListener;
    private OnTapListener mOnTapListener;
    private ValueAnimator mAnimator;
    private GestureDetector mGestureDetector;
    private Scroller mScroller;
    private float mOffset;
    private int mCurrentLine;
    private Object mFlag;
    private boolean isShowTimeline;
    private boolean isTouching;
    private boolean isFling;
    /**
     * Lyric display position, left/center/right
     */
    private int mTextGravity;

    /**
     * The play button clicks the listener,
     * and it should jump to the specified progress after clicking
     */
    public interface OnPlayClickListener {
        /***
         * @param view lyrics control
         * @param time selected progress
         * @return success
         */
        boolean onPlayClick(LrcView view, long time);
    }

    /**
     * Lyric control click listener
     */
    public interface OnTapListener {
        /**
         * Lyrics control is clicked
         * @param view lyrics control
         * @param x click coordinate x, relative to the control
         * @param y click coordinate y, relative to the control
         */
        void onTap(LrcView view, float x, float y);
    }

    public LrcView(Context context) {
        this(context, null);
    }

    public LrcView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public LrcView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(attrs);
    }

    private void init(AttributeSet attrs) {
        TypedArray ta = getContext().obtainStyledAttributes(attrs, R.styleable.LrcView);
        mCurrentTextSize = ta.getDimension(R.styleable.LrcView_lrcTextSize, getResources().getDimension(R.dimen.lrc_text_size));
        mNormalTextSize = ta.getDimension(R.styleable.LrcView_lrcNormalTextSize, getResources().getDimension(R.dimen.lrc_text_size));
        if (mNormalTextSize == 0) {
            mNormalTextSize = mCurrentTextSize;
        }

        mDividerHeight = ta.getDimension(R.styleable.LrcView_lrcDividerHeight, getResources().getDimension(R.dimen.lrc_divider_height));
        int defDuration = getResources().getInteger(R.integer.lrc_animation_duration);
        mAnimationDuration = ta.getInt(R.styleable.LrcView_lrcAnimationDuration, defDuration);
        mAnimationDuration = (mAnimationDuration < 0) ? defDuration : mAnimationDuration;
        mNormalTextColor = ta.getColor(R.styleable.LrcView_lrcNormalTextColor, getResources().getColor(R.color.lrc_normal_text_color));
        mCurrentTextColor = ta.getColor(R.styleable.LrcView_lrcCurrentTextColor, getResources().getColor(R.color.lrc_current_text_color));
        mTimelineTextColor = ta.getColor(R.styleable.LrcView_lrcTimelineTextColor, getResources().getColor(R.color.lrc_timeline_text_color));
        mDefaultLabel = ta.getString(R.styleable.LrcView_lrcLabel);
        mDefaultLabel = TextUtils.isEmpty(mDefaultLabel) ? getContext().getString(R.string.lrc_label) : mDefaultLabel;
        mLrcPadding = ta.getDimension(R.styleable.LrcView_lrcPadding, 0);
        mTimelineColor = ta.getColor(R.styleable.LrcView_lrcTimelineColor, getResources().getColor(R.color.lrc_timeline_color));
        float timelineHeight = ta.getDimension(R.styleable.LrcView_lrcTimelineHeight, getResources().getDimension(R.dimen.lrc_timeline_height));
        mPlayDrawable = ta.getDrawable(R.styleable.LrcView_lrcPlayDrawable);
        mPlayDrawable = (mPlayDrawable == null) ? getResources().getDrawable(R.drawable.lrc_play) : mPlayDrawable;
        mTimeTextColor = ta.getColor(R.styleable.LrcView_lrcTimeTextColor, getResources().getColor(R.color.lrc_time_text_color));
        float timeTextSize = ta.getDimension(R.styleable.LrcView_lrcTimeTextSize, getResources().getDimension(R.dimen.lrc_time_text_size));
        mTextGravity = ta.getInteger(R.styleable.LrcView_lrcTextGravity, LrcEntry.GRAVITY_CENTER);

        ta.recycle();

        mDrawableWidth = (int) getResources().getDimension(R.dimen.lrc_drawable_width);
        mTimeTextWidth = (int) getResources().getDimension(R.dimen.lrc_time_width);

        mLrcPaint.setAntiAlias(true);
        mLrcPaint.setTextSize(mCurrentTextSize);
        mLrcPaint.setTextAlign(Paint.Align.LEFT);
        mTimePaint.setAntiAlias(true);
        mTimePaint.setTextSize(timeTextSize);
        mTimePaint.setTextAlign(Paint.Align.CENTER);
        //noinspection SuspiciousNameCombination
        mTimePaint.setStrokeWidth(timelineHeight);
        mTimePaint.setStrokeCap(Paint.Cap.ROUND);
        mTimeFontMetrics = mTimePaint.getFontMetrics();

        mGestureDetector = new GestureDetector(getContext(), mSimpleOnGestureListener);
        mGestureDetector.setIsLongpressEnabled(false);
        mScroller = new Scroller(getContext());
    }

    /**
     * Set the font color of non-current line lyrics
     */
    public void setNormalColor(int normalColor) {
        mNormalTextColor = normalColor;
        postInvalidate();
    }

    /**
     * non-current line lyric text font size
     */
    public void setNormalTextSize(float size) {
        mNormalTextSize = size;
    }

    /**
     * Current lyric text font size
     */
    public void setCurrentTextSize(float size) {
        mCurrentTextSize = size;
    }

    /**
     * Set the font color of the current line lyrics
     */
    public void setCurrentColor(int currentColor) {
        mCurrentTextColor = currentColor;
        postInvalidate();
    }

    /**
     * Set the font color of the selected lyrics when dragging the lyrics
     */
    public void setTimelineTextColor(int timelineTextColor) {
        mTimelineTextColor = timelineTextColor;
        postInvalidate();
    }

    /**
     * Set the color of the timeline when dragging lyrics
     */
    public void setTimelineColor(int timelineColor) {
        mTimelineColor = timelineColor;
        postInvalidate();
    }

    /**
     * Set the time font color on the right side when dragging the lyrics
     */
    public void setTimeTextColor(int timeTextColor) {
        mTimeTextColor = timeTextColor;
        postInvalidate();
    }

    /**
     * Set whether the lyrics are allowed to be dragged
     *
     * @param draggable whether dragging is allowed
     * @param onPlayClickListener Set the click listener of the play button after the lyrics are dragged, if dragging is allowed, it cannot be null
     */
    public void setDraggable(boolean draggable, OnPlayClickListener onPlayClickListener) {
        if (draggable) {
            if (onPlayClickListener == null) {
                throw new IllegalArgumentException("if draggable == true, onPlayClickListener must not be null");
            }
            mOnPlayClickListener = onPlayClickListener;
        } else {
            mOnPlayClickListener = null;
        }
    }

    /**
     * Set the play button click listener
     *
     * @param onPlayClickListener If non-null, the lyrics dragging function will be activated, otherwise the lyrics dragging function will be disabled
     * @deprecated use {@link #setDraggable(boolean, OnPlayClickListener)} instead
     */
    @Deprecated
    public void setOnPlayClickListener(OnPlayClickListener onPlayClickListener) {
        mOnPlayClickListener = onPlayClickListener;
    }

    /**
     * Set the lyrics control click listener
     *
     * @param onTapListener lyrics control click listener
     */
    public void setOnTapListener(OnTapListener onTapListener) {
        mOnTapListener = onTapListener;
    }

    /**
     * Set the text displayed in the center of the screen when the lyrics are empty, such as "No lyrics yet"
     */
    public void setLabel(String label) {
        runOnUi(() -> {
            mDefaultLabel = label;
            invalidate();
        });
    }

    /**
     * Load lyrics file
     *
     * @param lrcFile lyrics file
     */
    public void loadLrc(File lrcFile) {
        loadLrc(lrcFile, null);
    }

    /**
     * Load bilingual lyrics files, the timestamps of lyrics in both languages need to be consistent
     *
     * @param mainLrcFile first language lyrics file
     * @param secondLrcFile second language lyrics file
     */
    public void loadLrc(File mainLrcFile, File secondLrcFile) {
        runOnUi(() -> {
            reset();

            StringBuilder sb = new StringBuilder("file://");
            sb.append(mainLrcFile.getPath());
            if (secondLrcFile != null) {
                sb.append("#").append(secondLrcFile.getPath());
            }
            String flag = sb.toString();
            setFlag(flag);
            new AsyncTask<File, Integer, List<LrcEntry>>() {
                @Override
                protected List<LrcEntry> doInBackground(File... params) {
                    return LrcUtils.parseLrc(params);
                }

                @Override
                protected void onPostExecute(List<LrcEntry> lrcEntries) {
                    if (getFlag() == flag) {
                        onLrcLoaded(lrcEntries);
                        setFlag(null);
                    }
                }
            }.execute(mainLrcFile, secondLrcFile);
        });
    }

    /**
     * Load lyrics text
     *
     * @param lrcText lyrics text
     */
    public void loadLrc(String lrcText) {
        loadLrc(lrcText, null);
    }

    /**
     * Load bilingual lyrics text, and the timestamps of lyrics in both languages need to be consistent
     *
     * @param mainLrcText first language lyrics text
     * @param secondLrcText second language lyrics text
     */
    public void loadLrc(String mainLrcText, String secondLrcText) {
        runOnUi(() -> {
            reset();

            StringBuilder sb = new StringBuilder("file://");
            sb.append(mainLrcText);
            if (secondLrcText != null) {
                sb.append("#").append(secondLrcText);
            }
            String flag = sb.toString();
            setFlag(flag);
            new AsyncTask<String, Integer, List<LrcEntry>>() {
                @Override
                protected List<LrcEntry> doInBackground(String... params) {
                    return LrcUtils.parseLrc(params);
                }

                @Override
                protected void onPostExecute(List<LrcEntry> lrcEntries) {
                    if (getFlag() == flag) {
                        onLrcLoaded(lrcEntries);
                        setFlag(null);
                    }
                }
            }.execute(mainLrcText, secondLrcText);
        });
    }

    /**
     * Load online lyrics, use utf-8 encoding by default
     *
     * @param lrcUrl The network address of the lyrics file
     */
    public void loadLrcByUrl(String lrcUrl) {
        loadLrcByUrl(lrcUrl, "utf-8");
    }

    /**
     * Load online lyrics
     *
     * @param lrcUrl The network address of the lyrics file
     * @param charset encoding format
     */
    public void loadLrcByUrl(String lrcUrl, String charset) {
        String flag = "url://" + lrcUrl;
        setFlag(flag);
        new AsyncTask<String, Integer, String>() {
            @Override
            protected String doInBackground(String... params) {
                return LrcUtils.getContentFromNetwork(params[0], params[1]);
            }

            @Override
            protected void onPostExecute(String lrcText) {
                if (getFlag() == flag) {
                    loadLrc(lrcText);
                }
            }
        }.execute(lrcUrl, charset);
    }

    /**
     * Whether the lyrics are valid
     *
     * @return true if the lyrics are valid, otherwise false
     */
    public boolean hasLrc() {
        return !mLrcEntryList.isEmpty();
    }

    /**
     * Refresh lyrics
     *
     * @param time current playing time
     */
    public void updateTime(long time) {
        runOnUi(() -> {
            if (!hasLrc()) {
                return;
            }

            int line = findShowLine(time);
            if (line != mCurrentLine) {
                mCurrentLine = line;
                if (!isShowTimeline) {
                    smoothScrollTo(line);
                } else {
                    invalidate();
                }
            }
        });
    }

    /**
     * Scroll lyrics to specified time
     *
     * @param time specified time
     * @deprecated please use {@link #updateTime(long)} instead
     */
    @Deprecated
    public void onDrag(long time) {
        updateTime(time);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (changed) {
            initPlayDrawable();
            initEntryList();
            if (hasLrc()) {
                smoothScrollTo(mCurrentLine, 0L);
            }
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int centerY = getHeight() / 2;

        // no lyrics file
        if (!hasLrc()) {
            mLrcPaint.setColor(mCurrentTextColor);
            @SuppressLint("DrawAllocation")
            StaticLayout staticLayout = new StaticLayout(mDefaultLabel, mLrcPaint,
                    (int) getLrcWidth(), Layout.Alignment.ALIGN_CENTER, 1f, 0f, false);
            drawText(canvas, staticLayout, centerY);
            return;
        }

        int centerLine = getCenterLine();

        if (isShowTimeline) {
            mPlayDrawable.draw(canvas);

            mTimePaint.setColor(mTimelineColor);
            canvas.drawLine(mTimeTextWidth, centerY, getWidth() - mTimeTextWidth, centerY, mTimePaint);

            mTimePaint.setColor(mTimeTextColor);
            String timeText = LrcUtils.formatTime(mLrcEntryList.get(centerLine).getTime());
            float timeX = getWidth() - mTimeTextWidth / 2;
            float timeY = centerY - (mTimeFontMetrics.descent + mTimeFontMetrics.ascent) / 2;
            canvas.drawText(timeText, timeX, timeY, mTimePaint);
        }

        canvas.translate(0, mOffset);

        float y = 0;
        for (int i = 0; i < mLrcEntryList.size(); i++) {
            if (i > 0) {
                y += ((mLrcEntryList.get(i - 1).getHeight() + mLrcEntryList.get(i).getHeight()) >> 1) + mDividerHeight;
            }
            if (i == mCurrentLine) {
                mLrcPaint.setTextSize(mCurrentTextSize);
                mLrcPaint.setColor(mCurrentTextColor);
            } else if (isShowTimeline && i == centerLine) {
                mLrcPaint.setColor(mTimelineTextColor);
            } else {
                mLrcPaint.setTextSize(mNormalTextSize);
                mLrcPaint.setColor(mNormalTextColor);
            }
            drawText(canvas, mLrcEntryList.get(i).getStaticLayout(), y);
        }
    }

    /**
     * Draw a line of lyrics
     *
     * @param y Lyric center Y coordinate
     */
    private void drawText(Canvas canvas, StaticLayout staticLayout, float y) {
        canvas.save();
        canvas.translate(mLrcPadding, y - (staticLayout.getHeight() >> 1));
        staticLayout.draw(canvas);
        canvas.restore();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
            isTouching = false;
            // 手指离开屏幕，启动延时任务，恢复歌词位置
            if (hasLrc() && isShowTimeline) {
                adjustCenter();
                postDelayed(hideTimelineRunnable, TIMELINE_KEEP_TIME);
            }
        }
        return mGestureDetector.onTouchEvent(event);
    }

    /**
     * Gesture listener
     */
    private GestureDetector.SimpleOnGestureListener mSimpleOnGestureListener = new GestureDetector.SimpleOnGestureListener() {
        // This click is only to stop the scrolling of the lyrics, and does not respond to the click event
        private boolean isTouchForStopFling = false;

        @Override
        public boolean onDown(MotionEvent e) {
            if (!hasLrc()) {
                return mOnTapListener != null;
            }
            isTouching = true;
            removeCallbacks(hideTimelineRunnable);
            if (isFling) {
                isTouchForStopFling = true;
                mScroller.forceFinished(true);
            } else {
                isTouchForStopFling = false;
            }
            return mOnPlayClickListener != null || mOnTapListener != null;
        }

        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
            if (!hasLrc() || mOnPlayClickListener == null) {
                return super.onScroll(e1, e2, distanceX, distanceY);
            }
            if (!isShowTimeline) {
                isShowTimeline = true;
            } else {
                mOffset += -distanceY;
                mOffset = Math.min(mOffset, getOffset(0));
                mOffset = Math.max(mOffset, getOffset(mLrcEntryList.size() - 1));
            }
            invalidate();
            return true;
        }

        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            if (!hasLrc() || mOnPlayClickListener == null) {
                return super.onFling(e1, e2, velocityX, velocityY);
            }
            if (isShowTimeline) {
                isFling = true;
                removeCallbacks(hideTimelineRunnable);
                mScroller.fling(0, (int) mOffset, 0, (int) velocityY, 0, 0, (int) getOffset(mLrcEntryList.size() - 1), (int) getOffset(0));
                return true;
            }
            return super.onFling(e1, e2, velocityX, velocityY);
        }

        @Override
        public boolean onSingleTapConfirmed(MotionEvent e) {
            if (hasLrc() && mOnPlayClickListener != null && isShowTimeline && mPlayDrawable.getBounds().contains((int) e.getX(), (int) e.getY())) {
                int centerLine = getCenterLine();
                long centerLineTime = mLrcEntryList.get(centerLine).getTime();
                // onPlayClick consumes to update the UI
                if (mOnPlayClickListener != null && mOnPlayClickListener.onPlayClick(LrcView.this, centerLineTime)) {
                    isShowTimeline = false;
                    removeCallbacks(hideTimelineRunnable);
                    mCurrentLine = centerLine;
                    invalidate();
                    return true;
                }
            } else if (mOnTapListener != null && !isTouchForStopFling) {
                mOnTapListener.onTap(LrcView.this, e.getX(), e.getY());
            }
            return super.onSingleTapConfirmed(e);
        }
    };

    private Runnable hideTimelineRunnable = new Runnable() {
        @Override
        public void run() {
            Log.d(TAG, "hideTimelineRunnable run");
            if (hasLrc() && isShowTimeline) {
                isShowTimeline = false;
                smoothScrollTo(mCurrentLine);
            }
        }
    };

    @Override
    public void computeScroll() {
        if (mScroller.computeScrollOffset()) {
            mOffset = mScroller.getCurrY();
            invalidate();
        }

        if (isFling && mScroller.isFinished()) {
            Log.d(TAG, "fling finish");
            isFling = false;
            if (hasLrc() && !isTouching) {
                adjustCenter();
                postDelayed(hideTimelineRunnable, TIMELINE_KEEP_TIME);
            }
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        removeCallbacks(hideTimelineRunnable);
        super.onDetachedFromWindow();
    }

    private void onLrcLoaded(List<LrcEntry> entryList) {
        if (entryList != null && !entryList.isEmpty()) {
            mLrcEntryList.addAll(entryList);
        }

        Collections.sort(mLrcEntryList);

        initEntryList();
        invalidate();
    }

    private void initPlayDrawable() {
        int l = (mTimeTextWidth - mDrawableWidth) / 2;
        int t = getHeight() / 2 - mDrawableWidth / 2;
        int r = l + mDrawableWidth;
        int b = t + mDrawableWidth;
        mPlayDrawable.setBounds(l, t, r, b);
    }

    private void initEntryList() {
        if (!hasLrc() || getWidth() == 0) {
            return;
        }

        for (LrcEntry lrcEntry : mLrcEntryList) {
            lrcEntry.init(mLrcPaint, (int) getLrcWidth(), mTextGravity);
        }

        mOffset = getHeight() / 2;
    }

    private void reset() {
        endAnimation();
        mScroller.forceFinished(true);
        isShowTimeline = false;
        isTouching = false;
        isFling = false;
        removeCallbacks(hideTimelineRunnable);
        mLrcEntryList.clear();
        mOffset = 0;
        mCurrentLine = 0;
        invalidate();
    }

    /**
     * Adjust the center row to the exact center
     */
    private void adjustCenter() {
        smoothScrollTo(getCenterLine(), ADJUST_DURATION);
    }

    /**
     * scroll to a row
     */
    private void smoothScrollTo(int line) {
        smoothScrollTo(line, mAnimationDuration);
    }

    /**
     * scroll to a row
     */
    private void smoothScrollTo(int line, long duration) {
        float offset = getOffset(line);
        endAnimation();

        mAnimator = ValueAnimator.ofFloat(mOffset, offset);
        mAnimator.setDuration(duration);
        mAnimator.setInterpolator(new LinearInterpolator());
        mAnimator.addUpdateListener(animation -> {
            mOffset = (float) animation.getAnimatedValue();
            invalidate();
        });
        LrcUtils.resetDurationScale();
        mAnimator.start();
    }

    /**
     * End scrolling animation
     */
    private void endAnimation() {
        if (mAnimator != null && mAnimator.isRunning()) {
            mAnimator.end();
        }
    }

    /**
     * Dichotomy finds the number of rows that should be displayed at the current time (the last row <= time)
     */
    private int findShowLine(long time) {
        int left = 0;
        int right = mLrcEntryList.size();
        while (left <= right) {
            int middle = (left + right) / 2;
            long middleTime = mLrcEntryList.get(middle).getTime();

            if (time < middleTime) {
                right = middle - 1;
            } else {
                if (middle + 1 >= mLrcEntryList.size() || time < mLrcEntryList.get(middle + 1).getTime()) {
                    return middle;
                }

                left = middle + 1;
            }
        }

        return 0;
    }

    /**
     * Get the number of rows currently in the center of the view
     */
    private int getCenterLine() {
        int centerLine = 0;
        float minDistance = Float.MAX_VALUE;
        for (int i = 0; i < mLrcEntryList.size(); i++) {
            if (Math.abs(mOffset - getOffset(i)) < minDistance) {
                minDistance = Math.abs(mOffset - getOffset(i));
                centerLine = i;
            }
        }
        return centerLine;
    }

    /**
     * Get the distance of the lyrics from the top of the view
     */
    private float getOffset(int line) {
        if (mLrcEntryList.get(line).getOffset() == Float.MIN_VALUE) {
            float offset = getHeight() / 2;
            for (int i = 1; i <= line; i++) {
                offset -= ((mLrcEntryList.get(i - 1).getHeight() + mLrcEntryList.get(i).getHeight()) >> 1) + mDividerHeight;
            }
            mLrcEntryList.get(line).setOffset(offset);
        }

        return mLrcEntryList.get(line).getOffset();
    }

    /**
     * Get lyrics width
     */
    private float getLrcWidth() {
        return getWidth() - mLrcPadding * 2;
    }

    /**
     * Run in the main looper
     */
    private void runOnUi(Runnable r) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            r.run();
        } else {
            post(r);
        }
    }

    private Object getFlag() {
        return mFlag;
    }

    private void setFlag(Object flag) {
        this.mFlag = flag;
    }
}
