package com.aj.sidemenu.widget;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.GradientDrawable.Orientation;
import android.os.Handler;
import android.support.v4.view.VelocityTrackerCompat;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.Scroller;

import com.aj.sidemenu.R;

public class SideMenuView extends FrameLayout
{
	private static final String TAG = "SideMenuView";
	
	private static final int MENU_POS = 0;
	private static final int CONTENT_POS = 1;

	public static final int OPEN_LEFT = 3;
	public static final int OPEN_RIGHT = 2;
	public static final int MENU_OPEN = 10;
	public static final int MENU_CLOSED = 11;
	public static final int DEFAULT_DURATION = 450;
	
	private Activity mActivity;

	private OnSideMenuToggleListener mListener;
	
	private SideMenuInternal mSideMenuInternal;
	
	public SideMenuView(Context context) 
	{
		super(context);
		mSideMenuInternal = new SideMenuInternal(context);
	}
	
	public SideMenuView(Context context, AttributeSet attrs) 
	{
		super(context, attrs);
		mSideMenuInternal = new SideMenuInternal(context, attrs);
	}
	
	public SideMenuView(Context context, AttributeSet attrs, int defStyle) 
	{
		super(context, attrs, defStyle);
		mSideMenuInternal = new SideMenuInternal(context, attrs, defStyle);
	}
	
	@Override
	protected void onAttachedToWindow() 
	{
		super.onAttachedToWindow();
		
		if(getChildCount() > 2)
		{
			throw new IllegalArgumentException("SideMenuView needs two views, one for content above and one for menu below");
		}
		
		View menu = getChildAt(MENU_POS);
		View content = getChildAt(CONTENT_POS);
		
		if(menu != null)
			mSideMenuInternal.setMenu(menu);
		
		if(mSideMenuInternal.mActionBarSlideEnabled)
		{	
			if(content.getParent() != null)
			{
				((ViewGroup)content.getParent()).removeView(content);
			}
			mActivity.setContentView(content);
			
			ViewGroup decor = (ViewGroup)mActivity.getWindow().getDecorView();
			ViewGroup decorChild = (ViewGroup)decor.getChildAt(0);
			decor.removeAllViews();
			
			mSideMenuInternal.mActionBarIsSplit = decorChild.getChildCount() == 3;
			
			mSideMenuInternal.setContent(decorChild);
			decor.addView(mSideMenuInternal);
		}
		else
		{
			if(content != null)
				mSideMenuInternal.setContent(content);
			addView(mSideMenuInternal);
		}
	}
	
	@Override
	protected void onDetachedFromWindow() 
	{
		super.onDetachedFromWindow();
	}
	
	public void setState(int state)
	{
		mSideMenuInternal.mState = state;
	}
	
	public void setMenuFading(boolean fade)
	{
		mSideMenuInternal.mFadeMenu = fade;
	}
	
	public void setContentFadign(boolean fade)
	{
		mSideMenuInternal.mFadeContent = fade;
	}
	
	public void setOnSideMenuToggleListener(OnSideMenuToggleListener listener)
	{
		mListener = listener;
	}
	
	public void open()
	{
		mSideMenuInternal.open();
	}
	
	public void openAnimated()
	{
		mSideMenuInternal.openAnimated();
	}
	
	public void openAnimatedForResult(Runnable runnable)
	{
		mSideMenuInternal.openAnimatedForResult(runnable);
	}
	
	public void close()
	{
		mSideMenuInternal.close();
	}
	
	public void closeAnimated()
	{
		mSideMenuInternal.closeAnimated();
	}
	
	public void closeAnimatedForResult(Runnable runnable)
	{
		mSideMenuInternal.closeAnimatedForResult(runnable);
	}
	
	public void toggle()
	{
		mSideMenuInternal.toggle();
	}
	
	public void toggleAnimated()
	{
		mSideMenuInternal.toggleAnimated();
	}
	
	private interface OnMenuStateListener
	{
		public void onMenuStateChange(int newState);
	}
	
	public interface OnSideMenuOpenListener
	{
		public void onOpen();
	}
	
	public interface OnSideMenuCloseListener
	{
		public void onClose();
	}
	
	public interface OnSideMenuToggleListener
	{
		public void onOpen();
		public void onClose();
	}
	
	public boolean isOpen()
	{
		return mSideMenuInternal.mState == MENU_OPEN;
	}
	
	public static boolean ValidViewGroup(ViewGroup vg) {
		return vg instanceof SideMenuInternal;
	}
	
	/*
	 * Internal SideMenu implementation
	 *  -Needed to capture ActionBar layouts without circular dependencies
	 */
	private class SideMenuInternal extends FrameLayout
	{
		private static final int DEFAULT_BEZEL_SIZE = 30; //dp
		private static final int DEFAULT_KEPT_THRESHOLD_PORT = 48; //dp
		private static final int DIRECTION_RIGHT = 1;
		private static final int DIRECTION_LEFT = 2;
		private static final int MAX_OVERLAY_ALPHA = 170;
		
		private static final float DEFAULT_KEPT_PERCENTAGE_LAND = 0.45f;
		private static final float PARALLAX_SPEED_RATIO = 0.2f;
		
		private boolean mFadeMenu = false;
		private boolean mFadeContent = false;
		private boolean mIsDragging = false;
		private boolean mIsScrolling = false;
		private boolean mIsTrackingMotion = false;
		private boolean mActionBarIsSplit = false;
		private boolean mActionBarSlideEnabled = false;
		
		private int mState = MENU_CLOSED;
		private int mOpenFrom = OPEN_RIGHT; 
		private int mDuration = DEFAULT_DURATION;
		
		private float mScreenWidth;
		private float mScreenHeight;
		private float mScreenDensity;
		private float mHalfWayPoint;
		
		private int mKeptThreshold;
		private int mBezelThreshold;
		private int mScaledTouchSlop;
		private int mMinFlingVelocity;
		private int mMaxFlingVelocity;
		private int mStatusBarHeight = 0;
		private int mApproxActionBarHeight = 0;
		
		private float mStartX;
		private float mStartY;

		private Scroller mScroller;
		private VelocityTracker mTracker = null;
		private Handler mHandler = new Handler();

		private Paint mOverlayPaint = new Paint();
		private GradientDrawable mShadowDrawable;
		
		public FrameLayout mMenuInternal;
		public FrameLayout mContentInternal;
		
		public SideMenuInternal(Context context) 
		{
			super(context);
			init(context, context.obtainStyledAttributes(R.styleable.SideMenuView));
		}
		
		public SideMenuInternal(Context context, AttributeSet attrs) 
		{
			super(context, attrs);
			init(context, context.obtainStyledAttributes(attrs, R.styleable.SideMenuView));
		}

		public SideMenuInternal(Context context, AttributeSet attrs, int defStyle) 
		{
			super(context, attrs, defStyle);
			init(context, context.obtainStyledAttributes(attrs, R.styleable.SideMenuView, defStyle, 0));
		}
		
		private void init(Context context, TypedArray a)
		{
			mActivity = (Activity)context;
			mScroller = new Scroller(mActivity, new DecelerateInterpolator());
		
			mOpenFrom = a.getInt(R.styleable.SideMenuView_direction, OPEN_RIGHT);
			mDuration = a.getInt(R.styleable.SideMenuView_speed, DEFAULT_DURATION);
			mFadeMenu = a.getBoolean(R.styleable.SideMenuView_fadeMenu, false);
			mFadeContent = a.getBoolean(R.styleable.SideMenuView_fadeContent, false);
			mActionBarSlideEnabled = a.getBoolean(R.styleable.SideMenuView_slideActionBar, false);
			
			DisplayMetrics metrics = new DisplayMetrics();
			((WindowManager)mActivity.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay().getMetrics(metrics);
			
			mScreenDensity = metrics.density;
			mScreenWidth = metrics.widthPixels;
			mScreenHeight= metrics.heightPixels;
			
			if(mActionBarSlideEnabled)
				mApproxActionBarHeight = (int)(48 * mScreenDensity + 0.5f);
			
			if(mActivity.getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT)
			{
				mKeptThreshold = a.getDimensionPixelSize(R.styleable.SideMenuView_keptThresholdPortrait, (int)(DEFAULT_KEPT_THRESHOLD_PORT * mScreenDensity + 0.5f));
			}
			else
			{
				mKeptThreshold = a.getDimensionPixelSize(R.styleable.SideMenuView_keptPercentageLandscape, (int)(mScreenWidth * DEFAULT_KEPT_PERCENTAGE_LAND));
			}
			
			int bezel = a.getDimensionPixelSize(R.styleable.SideMenuView_bezelSize, (int)(DEFAULT_BEZEL_SIZE * mScreenDensity + 0.5f));
			if(mOpenFrom == OPEN_RIGHT)
			{
				mBezelThreshold = (int)(mScreenWidth - bezel);
				mShadowDrawable = new GradientDrawable(Orientation.LEFT_RIGHT, new int[] {0xaa000000, 0x66000000, 0x22000000, 0x00000000});
			}
			else
			{
				mBezelThreshold = bezel;
				mShadowDrawable = new GradientDrawable(Orientation.RIGHT_LEFT, new int[] {0xaa000000, 0x66000000, 0x22000000, 0x00000000});
			}
			mHalfWayPoint = (mScreenWidth - mKeptThreshold) / 2;
			
			ViewConfiguration vc = ViewConfiguration.get(mActivity);
			mScaledTouchSlop = vc.getScaledTouchSlop() * 2;
			mMinFlingVelocity = vc.getScaledMinimumFlingVelocity();
			mMaxFlingVelocity = vc.getScaledMaximumFlingVelocity();
			
			mMenuInternal = new FrameLayout(mActivity);
			super.addView(mMenuInternal);
			mContentInternal = new FrameLayout(mActivity);
			super.addView(mContentInternal);
			
			a.recycle();
		}
		
		public void setMenu(View menu)
		{
			if(mMenuInternal.getChildCount() > 0)
			{
				mMenuInternal.removeAllViews();
			}
			if(menu.getParent() != null)
			{
				((ViewGroup)menu.getParent()).removeView(menu);
			}
			mMenuInternal.addView(menu);
		}
		
		public void setContent(View content)
		{
			if(mContentInternal.getChildCount() > 0)
			{
				mContentInternal.removeAllViews();
			}
			if(content.getParent() != null)
			{
				((ViewGroup)content.getParent()).removeView(content);
			}
			mContentInternal.addView(content);
		}
		
		@Override
		protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) 
		{
			super.onMeasure(widthMeasureSpec, heightMeasureSpec);
			
			if(mActionBarSlideEnabled && mStatusBarHeight == 0)
			{
				Rect r = new Rect();
				mActivity.getWindow().getDecorView().getWindowVisibleDisplayFrame(r);
				mStatusBarHeight = r.top;
			}
			
			if(getChildCount() > 2)
			{
				throw new IllegalArgumentException("Must have only two components, one above and one below");
			}
			
			int width = mMenuInternal.getMeasuredWidth();
			int height = mMenuInternal.getMeasuredHeight();
			int desiredWidth = (int)(mScreenWidth - mKeptThreshold);
			if(width > desiredWidth)
			{
				int newWidthSpec = MeasureSpec.makeMeasureSpec(desiredWidth, MeasureSpec.EXACTLY);
				int newHeightSpec = MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY);
				mMenuInternal.measure(newWidthSpec, newHeightSpec);
			}
		}
		
		@Override
		protected void onLayout(boolean changed, int left, int top, int right, int bottom) 
		{
			super.onLayout(changed, left, top, right, bottom);
			
			int l = mMenuInternal.getLeft();
			int r = mMenuInternal.getRight();
			int t = mMenuInternal.getTop();
			int b = mMenuInternal.getBottom();
			if(l < mKeptThreshold)
			{
				if(mOpenFrom == OPEN_RIGHT)
					mMenuInternal.layout(mKeptThreshold, t + mStatusBarHeight, mKeptThreshold + r, b);
				else
					mMenuInternal.layout(l, t + mStatusBarHeight, r, b);
			}
			
			if(mOpenFrom == OPEN_RIGHT)
				mShadowDrawable.setBounds(0, mContentInternal.getTop(), 15, mContentInternal.getBottom());
			else
				mShadowDrawable.setBounds(-15, mContentInternal.getTop(), 0, mContentInternal.getBottom());
			
			if(mState == MENU_OPEN)
			{
				open();
			}
		}

		@Override
		protected void dispatchDraw(Canvas canvas) 
		{
			super.dispatchDraw(canvas);
			
			if(mState == MENU_OPEN)
			{
				canvas.save();
				if(mOpenFrom == OPEN_RIGHT)
					canvas.translate(mContentInternal.getRight(), 0);
				else
					canvas.translate(mContentInternal.getLeft(), 0);
				mShadowDrawable.draw(canvas);
				canvas.restore();
			}
			
			int menuWidth = mMenuInternal.getWidth();
			int contentWidth = mContentInternal.getWidth();
			if(menuWidth != 0 && contentWidth != 0)
			{
				float menuOpenRatio = 0;
				if(mOpenFrom == OPEN_RIGHT)
					menuOpenRatio = (menuWidth - (mScreenWidth - mContentInternal.getRight())) / (float)menuWidth;
				else
					menuOpenRatio = (menuWidth - mContentInternal.getLeft()) / (float)menuWidth;
				
				float contentOpenRatio = 0;
				if(mOpenFrom == OPEN_RIGHT)
					contentOpenRatio = (contentWidth - mContentInternal.getRight()) / (float)contentWidth;
				else
					contentOpenRatio = (contentWidth - mContentInternal.getLeft()) / (float)contentWidth;
					
				if(mFadeMenu)
				{
					drawMenuOverlay(canvas, menuOpenRatio);
				}
				if(mFadeContent)
				{
					drawContentOverlay(canvas, contentOpenRatio);
				}
			}
		}
		
		private void drawMenuOverlay(Canvas canvas, float openRatio)
		{
			final Paint paint = mOverlayPaint;
			final int alpha = (int)(MAX_OVERLAY_ALPHA * openRatio);
			if(alpha > 0)
			{
				paint.setColor(Color.argb(alpha, 0, 0, 0));
				if(mOpenFrom == OPEN_RIGHT)
					canvas.drawRect(mContentInternal.getRight(), 0, mScreenWidth, getHeight(), paint);
				else
					canvas.drawRect(0, 0, mContentInternal.getLeft(), getHeight(), paint);
			}
		}
		
		private void drawContentOverlay(Canvas canvas, float openRatio)
		{
			final Paint paint = mOverlayPaint;
			final int alpha = (int)(MAX_OVERLAY_ALPHA * openRatio);
			if(alpha > 0)
			{
				paint.setColor(Color.argb(alpha, 0, 0, 0));
				if(mOpenFrom == OPEN_RIGHT)
					canvas.drawRect(0, 0, mContentInternal.getRight(), getHeight(), paint);
				else
					canvas.drawRect(mContentInternal.getLeft(), 0, mScreenWidth, getHeight(), paint);
			}
		}
		
		@Override
		public boolean onInterceptTouchEvent(MotionEvent event) 
		{
			int action = event.getAction();
			float x = event.getX();
			float y = event.getY();
			if(action == MotionEvent.ACTION_DOWN)
			{
				if(mState == MENU_CLOSED) 
				{
					if(mIsScrolling)
					{
						if(mOpenFrom == OPEN_RIGHT)
						{
							if(x < mContentInternal.getRight())
							{
								cancelScroll();
								mIsDragging = true;
								mIsTrackingMotion = true;
							}
							else
							{
								mIsTrackingMotion = false;
							}
						}
						else
						{
							if(x > mContentInternal.getLeft())
							{
								cancelScroll();
								mIsDragging = true;
								mIsTrackingMotion = true;
							}
							else
							{
								mIsTrackingMotion = false;
							}
						}
					}
					else if(y > mStatusBarHeight + mApproxActionBarHeight && (mActionBarIsSplit?(y < (int)(mScreenHeight - mApproxActionBarHeight)):true))
					{
						if(mOpenFrom == OPEN_RIGHT)
							mIsTrackingMotion = x > mBezelThreshold;
						else
							mIsTrackingMotion = x < mBezelThreshold;
					}
				}
				else
				{
					if(mIsScrolling)
					{
						if(mOpenFrom == OPEN_RIGHT)
						{
							if(x < mContentInternal.getRight())
							{
								cancelScroll();
								mIsDragging = true;
								mIsTrackingMotion = true;
							}
							else
							{
								mIsTrackingMotion = false;
							}
						}
						else
						{
							if(x > mContentInternal.getLeft())
							{
								cancelScroll();
								mIsDragging = true;
								mIsTrackingMotion = true;
							}
							else
							{
								mIsTrackingMotion = false;
							}
						}
					}
					else
					{
						if(mOpenFrom == OPEN_RIGHT)
							mIsTrackingMotion = x < mContentInternal.getRight();
						else
							mIsTrackingMotion = x > mContentInternal.getLeft();
					}
				}
			}
			mStartX = x;
			mStartY = y;
			return mIsTrackingMotion;
		}
		
		@Override
		public boolean onTouchEvent(MotionEvent event) 
		{
			if(!mIsTrackingMotion)
			{
				return super.onTouchEvent(event);
			}
			
			int action = event.getAction();
			switch(action)
			{
			case MotionEvent.ACTION_DOWN:
				if(mTracker == null)
				{
					mTracker = VelocityTracker.obtain();
				}
				else
				{
					mTracker.clear();
				}
				mTracker.addMovement(event);
				break;
			case MotionEvent.ACTION_MOVE:
			{
				float x = event.getX();
				float y = event.getY();
				int dir = x < mStartX?DIRECTION_LEFT:DIRECTION_RIGHT;
				
				mTracker.addMovement(event);
				
				if(mIsDragging)
				{
					if(mOpenFrom == OPEN_RIGHT)
						mState = (mContentInternal.getRight() < mScreenWidth) ? MENU_OPEN : MENU_CLOSED;
					else
						mState = (mContentInternal.getLeft() > 0) ? MENU_OPEN : MENU_CLOSED;
					
					int dx = (int)(x - mStartX);
					
					if(mOpenFrom == OPEN_RIGHT)
					{
						if(dir == DIRECTION_LEFT && (mContentInternal.getRight() - Math.abs(dx) >= mKeptThreshold))
						{
							mContentInternal.offsetLeftAndRight(dx);
							nudgeMenu();
							invalidate();
							mStartX = x;
						}
						else if(dir == DIRECTION_RIGHT && (mContentInternal.getRight() + Math.abs(dx) <= mScreenWidth))
						{
							mContentInternal.offsetLeftAndRight(dx);
							nudgeMenu();
							invalidate();
							mStartX = x;
						}
					}
					else
					{
						if(dir == DIRECTION_LEFT && (mContentInternal.getLeft() - Math.abs(dx) >= 0))
						{
							mContentInternal.offsetLeftAndRight(dx);
							nudgeMenu();
							invalidate();
							mStartX = x;
						}
						else if(dir == DIRECTION_RIGHT && (mContentInternal.getLeft() + Math.abs(dx) <= (mScreenWidth - mKeptThreshold)))
						{
							mContentInternal.offsetLeftAndRight(dx);
							nudgeMenu();
							invalidate();
							mStartX = x;
						}
					}
				}
				else
				{
					boolean bigSlop = Math.hypot((mStartX - x), (mStartY - y)) > mScaledTouchSlop;
					boolean correctDirection = mState == MENU_CLOSED ? 
							(mOpenFrom == OPEN_RIGHT?dir == DIRECTION_LEFT:dir == DIRECTION_RIGHT):
								(mOpenFrom == OPEN_RIGHT?dir == DIRECTION_RIGHT:dir == DIRECTION_LEFT);
					mIsDragging = bigSlop && correctDirection;
				}
				break;
			}
			case MotionEvent.ACTION_UP:
			{
				float x = event.getX();
				
				mTracker.addMovement(event);
				mTracker.computeCurrentVelocity(1000);
				float v = VelocityTrackerCompat.getXVelocity(mTracker, 0);
				float s = Math.abs(v);
				
				if(!mIsDragging)
				{
					if(mState == MENU_OPEN)
					{
						closeAnimated();
					}
				}
				else
				{
					if(s > mMinFlingVelocity)
					{
						if(s > mMaxFlingVelocity)
						{
							s = mMaxFlingVelocity;
						}
						int duration = computeDuration(s);
						if(mOpenFrom == OPEN_RIGHT)
						{
							if(v < 0)
							{
								openInternal(duration, null);
							}
							else
							{
								closeInternal(duration, null);
							}
						}
						else
						{
							if(v < 0)
							{
								closeInternal(duration, null);
							}
							else
							{
								openInternal(duration, null);
							}
						}
					}
					else
					{
						if(mState == MENU_CLOSED && x > mHalfWayPoint)
						{
							if(mOpenFrom == OPEN_RIGHT)
								closeAnimated();
							else
								openAnimated();
						}
						else if(mState == MENU_CLOSED && x < mHalfWayPoint)
						{
							if(mOpenFrom == OPEN_RIGHT)
								openAnimated();
							else
								closeAnimated();
						}
						else if(mState == MENU_OPEN && x < mHalfWayPoint)
						{
							if(mOpenFrom == OPEN_RIGHT)
								openAnimated();
							else
								closeAnimated();
						}
						else if(mState == MENU_OPEN && x > mHalfWayPoint)
						{
							if(mOpenFrom == OPEN_RIGHT)
								closeAnimated();
							else
								openAnimated();
						}
					}
				}
				
				mTracker.recycle();
				mIsTrackingMotion = false;
				mIsDragging = false;
				break;
			}
			}
			return true;
		}
		
		private int computeDuration(float v)
		{
			float m = -0.02f;
			float b = 270;
			return (int)((m * v) + b);
		}
		
		public void toggle()
		{
			if(mState == MENU_OPEN)
			{
				close();
			}
			else
			{
				open();
			}
		}
		
		public void toggleAnimated()
		{
			toggleInternal(mDuration);
		}
		
		private void toggleInternal(int duration)
		{
			if(mState == MENU_OPEN)
			{
				closeInternal(duration, null);
			}
			else
			{
				openInternal(duration, null);
			}
		}
		
		public void open()
		{
			if(mIsScrolling)
			{
				cancelScroll();
			}
			
			mState = MENU_OPEN;
			int dx = 0;
			if(mOpenFrom == OPEN_RIGHT)
				dx = -1*(int)(mContentInternal.getRight() - mKeptThreshold);
			else
				dx = (int)(mScreenWidth - mKeptThreshold);
			mContentInternal.offsetLeftAndRight(dx);
			invalidate();
			if(mListener != null)
			{
				mListener.onOpen();
			}
		}
		
		public void openAnimated()
		{
			openInternal(mDuration, null);
		}
		
		public void openAnimatedForResult(Runnable runnable)
		{
			openInternal(mDuration, runnable);
		}
		
		private void openInternal(int duration, final Runnable runnable)
		{
			if(mIsScrolling)
			{
				cancelScroll();
			}
			
			mState = MENU_OPEN;
			int xi = mOpenFrom == OPEN_RIGHT?mContentInternal.getRight():mContentInternal.getLeft();
			int dx = mOpenFrom == OPEN_RIGHT?(int)(mContentInternal.getRight() - mKeptThreshold):-1*(int)((mScreenWidth - mKeptThreshold) - mContentInternal.getLeft());
			mScroller.startScroll(xi, 0, dx, 0, duration);
			mHandler.post(new SlideRunnable(xi, MENU_OPEN, new OnMenuStateListener() 
			{	
				@Override
				public void onMenuStateChange(int newState) 
				{
					mState = newState;
					if(runnable != null)
					{
						runnable.run();
					}
					if(mListener != null)
					{
						mListener.onOpen();
					}
				}
			}));
		}
		
		public void close()
		{
			if(mIsScrolling)
			{
				cancelScroll();
			}
			
			int dx = 0;
			if(mOpenFrom == OPEN_RIGHT)
				dx = -1*(int)(mContentInternal.getRight() - mScreenWidth);
			else
				dx = -1*mContentInternal.getLeft();
			mContentInternal.offsetLeftAndRight(dx);
			invalidate();
			mState = MENU_CLOSED;
			if(mListener != null)
			{
				mListener.onClose();
			}
		}
		
		public void closeAnimated()
		{
			closeInternal(mDuration, null);
		}
		
		public void closeAnimatedForResult(Runnable runnable)
		{
			closeInternal(mDuration, runnable);
		}
		
		private void closeInternal(int duration, final Runnable runnable)
		{
			if(mIsScrolling)
			{
				cancelScroll();
			}
			
			int xi = mOpenFrom == OPEN_RIGHT?mContentInternal.getRight():mContentInternal.getLeft();
			int dx = mOpenFrom == OPEN_RIGHT?(int)(mContentInternal.getRight() - mScreenWidth):mContentInternal.getLeft();
			mScroller.startScroll(xi, 0, dx, 0, duration);
			mHandler.post(new SlideRunnable(xi, MENU_CLOSED, new OnMenuStateListener() 
			{	
				@Override
				public void onMenuStateChange(int newState) 
				{
					mState = newState;
					if(runnable != null)
					{
						runnable.run();
					}
					if(mListener != null)
					{
						mListener.onClose();
					}
				}
			}));
		}
		
		public void cancelScroll()
		{
			mHandler.removeCallbacksAndMessages(null);
			mIsScrolling = false;
		}
		
		private void nudgeMenu()
		{
			final int width = mMenuInternal.getWidth();
			if(width != 0)
			{
				int dx = 0;
				if(mOpenFrom == OPEN_RIGHT)
					dx = (int)((width - (mScreenWidth - mContentInternal.getRight())) * PARALLAX_SPEED_RATIO) - (mMenuInternal.getLeft() - mKeptThreshold);
				else
					dx = (int)(((mContentInternal.getLeft() - width) * PARALLAX_SPEED_RATIO) - mMenuInternal.getLeft());
				mMenuInternal.offsetLeftAndRight(dx);
			}
		}
		
		private class SlideRunnable implements Runnable
		{
			int lastX = 0;
			int endState;
			OnMenuStateListener listener;
			
			public SlideRunnable(int x, int endState, OnMenuStateListener listener)
			{
				lastX = x;
				this.endState = endState;
				this.listener = listener;
			}
			
			@Override
			public void run() 
			{
				if(mScroller.isFinished())
				{
					mIsScrolling = false;
					if(listener != null)
					{
						listener.onMenuStateChange(endState);
						listener = null;
					}
					return;
				}
				
				mIsScrolling = true;
				boolean more = mScroller.computeScrollOffset();
				int x = mScroller.getCurrX();
				int dx = lastX - x;
				if(dx != 0)
				{
					mContentInternal.offsetLeftAndRight(dx);
					nudgeMenu();
					invalidate();
					lastX = x;
				}
				
				if(more)
				{
					mHandler.postDelayed(this, 16);
				}
				else
				{
					mIsScrolling = false;
					if(listener != null)
					{
						listener.onMenuStateChange(endState);
						listener = null;
					}
				}
			}
		}
	}
}
