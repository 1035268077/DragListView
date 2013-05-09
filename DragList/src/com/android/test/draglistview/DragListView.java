package com.android.test.draglistview;

import java.util.ArrayList;
import java.util.List;

import android.R.integer;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.Animation;
import android.view.animation.ScaleAnimation;
import android.view.animation.TranslateAnimation;
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.ListView;

import com.fengjian.test.R;
import com.fengjian.test.draggridview2.DateAdapter;

/**
 * ������
 *
 */
public class DragListView extends ListView {

	private WindowManager windowManager;// windows���ڿ�����
	private WindowManager.LayoutParams windowParams;// ���ڿ�����ק�����ʾ�Ĳ���

	private int scaledTouchSlop;// �жϻ�����һ������,scroll��ʱ����õ�(24)

	private ImageView dragImageView;// ����ק����(item)����ʵ����һ��ImageView
	private int startPosition;// ��ָ�϶���ԭʼ���б��е�λ��
	private int dragPosition;// ��ָ���׼���϶���ʱ��,��ǰ�϶������б��е�λ��.
	private int lastPosition;// ��ָ���׼���϶���ʱ��,��ǰ�϶������б��е�λ��.
	
	private ViewGroup dragItemView = null;//�϶�ʱ���ص�view

	private int dragPoint;// �ڵ�ǰ�������е�λ��
	private int dragOffset;// ��ǰ��ͼ����Ļ�ľ���(����ֻʹ����y������)

	private int upScrollBounce;// �϶���ʱ�򣬿�ʼ���Ϲ����ı߽�
	private int downScrollBounce;// �϶���ʱ�򣬿�ʼ���¹����ı߽�

	private final static int step = 1;// ListView ��������.

	private int current_Step;// ��ǰ����.

	private boolean isLock;// �Ƿ�����.
	
	private ItemInfo mDragItemInfo;
	private boolean isMoving = false;
	private boolean isDragItemMoving = false;
	
	private int mItemVerticalSpacing = 0;
	
	private boolean bHasGetSapcing = false;

	public static final int MSG_DRAG_STOP = 0x1001;
	public static final int MSG_DRAG_MOVE = 0x1002;
	private static final int ANIMATION_DURATION = 200;
	
	Handler mHandler = new Handler() {
		public void handleMessage(android.os.Message msg) {
			switch (msg.what) {
			case MSG_DRAG_STOP:
				stopDrag();
				onDrop(msg.arg1);
				break;
			case MSG_DRAG_MOVE:
				onDrag(msg.arg1);
				break;
			default:
				break;
			}
		};
	};
	
	public void setLock(boolean isLock) {
		this.isLock = isLock;
	}

	public DragListView(Context context, AttributeSet attrs) {
		super(context, attrs);
		setLayerType(View.LAYER_TYPE_HARDWARE, null);
		scaledTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
		mDragItemInfo = new ItemInfo();
		init();
	}
	
	private void init(){
		windowManager = (WindowManager) getContext().getSystemService("window");
//		for(int i=0; i<6; i++){
//			listOrder.add(i);
//		}
	}

	private void getSpacing(){
		bHasGetSapcing = true;
		
		upScrollBounce = getHeight() / 3;// ȡ�����Ϲ����ı߼ʣ����Ϊ�ÿؼ���1/3
		downScrollBounce = getHeight() * 2 / 3;// ȡ�����¹����ı߼ʣ����Ϊ�ÿؼ���2/3
		
		int[] tempLocation0 = new int[2];
		int[] tempLocation1 = new int[2];
		
		ViewGroup itemView0 = (ViewGroup) getChildAt(0);//��һ��
		ViewGroup itemView1  = (ViewGroup) getChildAt(1);//�ڶ���
		
		if(itemView0 != null){
			itemView0.getLocationOnScreen(tempLocation0);
		}else{
			return;
		}
		
		if(itemView1 != null){
			itemView1.getLocationOnScreen(tempLocation1);
			mItemVerticalSpacing = Math.abs(tempLocation1[1] - tempLocation0[1]);
		}else{
			return;
		}
	}
	
	/***
	 * touch�¼����� �������ҽ�����Ӧ���أ�
	 */
	@Override
	public boolean onInterceptTouchEvent(MotionEvent ev) {
		// ����
		if (ev.getAction() == MotionEvent.ACTION_DOWN && !isLock && !isMoving && !isDragItemMoving) {
			int x = (int) ev.getX();// ��ȡ�����ListView��x����
			int y = (int) ev.getY();// ��ȡ��Ӧ��ListView��y����
			lastPosition = startPosition = dragPosition = pointToPosition(x, y);
			// ��Ч�����д���
			if (dragPosition == AdapterView.INVALID_POSITION) {
				return super.onInterceptTouchEvent(ev);
			}
			if(false == bHasGetSapcing){
				getSpacing();
			}
			
			// ��ȡ��ǰλ�õ���ͼ(�ɼ�״̬)
			ViewGroup dragger = (ViewGroup) getChildAt(dragPosition
					- getFirstVisiblePosition());

			DragListAdapter adapter = (DragListAdapter) getAdapter();
			
			mDragItemInfo.obj = adapter.getItem(dragPosition
					- getFirstVisiblePosition());
			
			// ��ȡ����dragPoint��ʵ����������ָ��item���еĸ߶�.
			dragPoint = y - dragger.getTop();
			// ���ֵ�ǹ̶���:��ʵ����ListView����ؼ�����Ļ����ľ��루һ��Ϊ������+״̬����.
			dragOffset = (int) (ev.getRawY() - y);

			// ��ȡ����ק��ͼ��
			View draggerIcon = dragger.findViewById(R.id.drag_list_item_image);

			// x > dragger.getLeft() - 20��仰Ϊ�˸��õĴ�����-20����ʡ�ԣ�
			if (draggerIcon != null && x > draggerIcon.getLeft() - 20) {
				
				dragItemView = dragger;
				
				dragger.destroyDrawingCache();
				dragger.setDrawingCacheEnabled(true);// ����cache.
				dragger.setBackgroundColor(0x55555555);
				Bitmap bm = Bitmap.createBitmap(dragger.getDrawingCache(true));// ����cache����һ���µ�bitmap����.
				hideDropItem();
				adapter.setInvisiblePosition(startPosition);
//				Animation animation = getScaleAnimation();
//				dragger.setVisibility(View.INVISIBLE);
//				dragger.startAnimation(animation);
//				dragger.removeAllViews();
				adapter.notifyDataSetChanged();		
				startDrag(bm, y);// ��ʼ��Ӱ��
				isMoving = false;
				
				adapter.copyList();
			}
			return false;
		}

		return super.onInterceptTouchEvent(ev);
	}

	public Animation getScaleAnimation(){
		Animation scaleAnimation= new   
			     ScaleAnimation(0.0f,0.0f,0.0f,0.0f,Animation.RELATIVE_TO_SELF,0.5f,Animation.RELATIVE_TO_SELF,0.5f);
		scaleAnimation.setFillAfter(true);
//		scaleAnimation.setDuration(300);	
		return scaleAnimation;
	}
	
	private void hideDropItem(){
		final DragListAdapter adapter = (DragListAdapter)this.getAdapter();
		adapter.showDropItem(false);
	}
	
	/**
	 * �����¼�����
	 */
	@Override
	public boolean onTouchEvent(MotionEvent ev) {
		// item��view��Ϊ�գ��һ�ȡ��dragPosition��Ч
		if (dragImageView != null && dragPosition != INVALID_POSITION
				&& !isLock) {
			int action = ev.getAction();
			switch (action) {
			case MotionEvent.ACTION_UP:
				int upY = (int) ev.getY();
				stopDrag();
				onDrop(upY);
//				if(false == isMoving){
//					mHandler.sendMessage(mHandler.obtainMessage(MSG_DRAG_STOP, upY, 0));
//				}else{
//					mHandler.sendMessageDelayed(mHandler.obtainMessage(MSG_DRAG_STOP, upY, 0), ANIMATION_DURATION);
//				}
				break;
			case MotionEvent.ACTION_MOVE:
				int moveY = (int) ev.getY();
//				mHandler.sendMessage(mHandler.obtainMessage(MSG_DRAG_MOVE, moveY, 0));
				onDrag(moveY);
//				test(moveY);
				testAnimation(moveY);
//				OnMove(moveY);
				break;
			case MotionEvent.ACTION_DOWN:
				break;
			default:
				break;
			}
			return true;// ȡ��ListView����.
		}

		return super.onTouchEvent(ev);
	}

//	private void test(int y){
//		int tempPosition = pointToPosition(0, y);
//		if (tempPosition == INVALID_POSITION) {
//			return;
//		}
//		if(tempPosition != AdapterView.INVALID_POSITION && tempPosition != dragPosition){
//			dropPosition = tempPosition;
//		}
//		onChange(y);
//	}
	
	private boolean isSameDragDirection = true;
	private int lastFlag = -1; //-1,0 == down,1== up
	
	private int mFirstVisiblePosition, mLastVisiblePosition;
	private int mCurFirstVisiblePosition, mCurLastVisiblePosition;
	
	private boolean isNormal = true;
	private int turnUpPosition, turnDownPosition;
	
//	private List<int[2]> listOrder = new ArrayList<Integer>();
	
	private void onChangeCopy(int last, int current) {
		DragListAdapter adapter = (DragListAdapter) getAdapter();
		if (last != current) {
			adapter.exchangeCopy(last, current);
			Log.i("wanggang", "onChange");
		}

	}
	
	private void testAnimation(int y){
		final DragListAdapter adapter = (DragListAdapter) getAdapter();
		int tempPosition = pointToPosition(0, y);
//		Log.i("wanggang", "1111111 tempPosition " + tempPosition);
		if (tempPosition == INVALID_POSITION || tempPosition == lastPosition) {
			return;
		}
		mFirstVisiblePosition = getFirstVisiblePosition();
//		if(startPosition < mFirstVisiblePosition){
//			startPosition = mFirstVisiblePosition;
//			adapter.setInvisiblePosition(startPosition);
//			Log.i("wanggang", "setInvisiblePosition " + startPosition);
//		}
		dragPosition = tempPosition;
//		Log.i("wanggang", "tempPosition " + tempPosition);
//		Log.i("wanggang", "lastPosition " + lastPosition);
		
//		onChange2(lastPosition, dragPosition);
		onChangeCopy(lastPosition, dragPosition);
		int MoveNum = tempPosition - lastPosition;
		int count = Math.abs(MoveNum);
		for(int i=1; i<=count; i++){
			int xAbsOffset, yAbsOffset;
			//����drag
			if(MoveNum > 0){
				if(lastFlag == -1){
					lastFlag = 0;
					isSameDragDirection = true;
				}
				if(lastFlag == 1){
					turnUpPosition = tempPosition;
					lastFlag = 0;
					isSameDragDirection = !isSameDragDirection;
				}
				if(isSameDragDirection){
					holdPosition = lastPosition + 1;
				}else{
					if(startPosition < tempPosition){
						holdPosition = lastPosition + 1;
						isSameDragDirection = !isSameDragDirection;
					}else{
						holdPosition = lastPosition;
					}
				}
				xAbsOffset = 0;
				yAbsOffset = - mItemVerticalSpacing;
				lastPosition++;
			}
			//����drag
			else{
				if(lastFlag == -1){
					lastFlag = 1;
					isSameDragDirection = true;
				}
				if(lastFlag == 0){
					turnDownPosition = tempPosition;
					lastFlag = 1;
					isSameDragDirection = !isSameDragDirection;
				}
				if(isSameDragDirection){
					holdPosition = lastPosition -1;
				}else{
					if(startPosition > tempPosition){
						holdPosition = lastPosition -1;
						isSameDragDirection = !isSameDragDirection;
					}else{
						holdPosition = lastPosition;
					}
				}
				xAbsOffset = 0;
				yAbsOffset = mItemVerticalSpacing;
				lastPosition--;
			}
			
//			if(isSameDragDirection){
//				adapter.setHoldPosition(tempPosition);
//			}else{
//				adapter.setHoldPosition(lastPosition);
//			}
			
			Log.i("wanggang", "getFirstVisiblePosition() = " + getFirstVisiblePosition());
			Log.i("wanggang", "getLastVisiblePosition() = " + getLastVisiblePosition());
			
//			adapter.setInvisiblePosition(tempPosition);
			
			adapter.setHeight(mItemVerticalSpacing);
			adapter.setIsSameDragDirection(isSameDragDirection);
			adapter.setLastFlag(lastFlag);
//			adapter.setHoldPosition(getFirstVisiblePosition() + 3);
			
	//		ViewGroup invisibleView = (ViewGroup)getChildAt(startPosition - getFirstVisiblePosition());
	//		Animation animation2;
	//		int fromY = (tempPosition - startPosition -1) * mItemVerticalSpacing;
	//		int toY = tempPosition * mItemVerticalSpacing;
	//		animation2 = getAnimation(fromY, toY);
	//	    
	//		invisibleView.startAnimation(animation2);
//			ViewGroup invisibleView = (ViewGroup)getChildAt(tempPosition - getFirstVisiblePosition());
//			Animation animation2 = getScaleAnimation();
//			invisibleView.startAnimation(animation2);
			
		    ViewGroup moveView = (ViewGroup)getChildAt(holdPosition - getFirstVisiblePosition());
		    
			//Animation animation = getMoveAnimation(Xoffset,Yoffset);
		    Animation animation;
		    if(isSameDragDirection){
		    	animation = getFromSelfAnimation(xAbsOffset, yAbsOffset);
		    }else{
		    	animation = getToSelfAnimation(xAbsOffset, -yAbsOffset);
		    }
		    moveView.startAnimation(animation);
		}
	}
	
	private void onDrop(int x,int y){
		final DragListAdapter adapter = (DragListAdapter) getAdapter();
		adapter.setInvisiblePosition(-1);
		adapter.showDropItem(true);
		adapter.notifyDataSetChanged();	
		
//		doDropAnimation(x,y);
	}
	
	/**
	 * ׼���϶�����ʼ���϶����ͼ��
	 * 
	 * @param bm
	 * @param y
	 */
	private void startDrag(Bitmap bm, int y) {
		// stopDrag();
		/***
		 * ��ʼ��window.
		 */
		windowParams = new WindowManager.LayoutParams();
		windowParams.gravity = Gravity.TOP;
		windowParams.x = 0;
		windowParams.y = y - dragPoint + dragOffset;
		windowParams.width = WindowManager.LayoutParams.WRAP_CONTENT;
		windowParams.height = WindowManager.LayoutParams.WRAP_CONTENT;

		windowParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE// �����ȡ����
				| WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE// ������ܴ����¼�
				| WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON// �����豸���������������Ȳ��䡣
				| WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;// ����ռ��������Ļ��������Χ��װ�α߿�����״̬�������˴����迼�ǵ�װ�α߿�����ݡ�

		// windowParams.format = PixelFormat.TRANSLUCENT;// Ĭ��Ϊ��͸�����������͸��Ч��.
		windowParams.windowAnimations = 0;// ������ʹ�õĶ�������
		
		windowParams.alpha = 0.8f;
		windowParams.format = PixelFormat.TRANSLUCENT;

		ImageView imageView = new ImageView(getContext());
		imageView.setImageBitmap(bm);
		
		windowManager.addView(imageView, windowParams);
		dragImageView = imageView;
	}

	/**
	 * �϶�ִ�У���Move������ִ��
	 * 
	 * @param y
	 */
	public void onDrag(int y) {
		int drag_top = y - dragPoint;// ��קview��topֵ���ܣ�0�����������.
		if (dragImageView != null && drag_top >= 0) {
			windowParams.alpha = 1.0f;
			windowParams.y = y - dragPoint + dragOffset;
			windowManager.updateViewLayout(dragImageView, windowParams);// ʱʱ�ƶ�.
		}
//		// Ϊ�˱��⻬�����ָ��ߵ�ʱ�򣬷���-1������
//		int tempPosition = pointToPosition(0, y);
//		if (tempPosition != INVALID_POSITION) {
//			dragPosition = tempPosition;
//		}
//
//		onChange(y);// ʱʱ����

		doScroller(y);// listview�ƶ�.
		
	}

	/***
	 * ListView���ƶ�.
	 * Ҫ�����ƶ�ԭ�������ƶ����¶˵�ʱ��ListView���ϻ����������ƶ����϶˵�ʱ��ListViewҪ���»��������ú�ʵ�ʵ��෴.
	 * 
	 */

	private boolean isScroll = false;
	
	public void doScroller(int y) {
		// ListView��Ҫ�»�
		if (y < upScrollBounce) {
			current_Step = step + (upScrollBounce - y) / 10;// ʱʱ����
		}// ListView��Ҫ�ϻ�
		else if (y > downScrollBounce) {
			current_Step = -(step + (y - downScrollBounce)) / 10;// ʱʱ����
		} else {
			isScroll = false;
			current_Step = 0;
		}

		// ��ȡ����ק������λ�ü���ʾitem��Ӧ��view�ϣ�ע������ʾ���֣���position��
		View view = getChildAt(dragPosition - getFirstVisiblePosition());
		// ���������ķ���setSelectionFromTop()
		setSelectionFromTop(dragPosition, view.getTop() + current_Step);

	}

	/**
	 * ֹͣ�϶���ɾ��Ӱ��
	 */
	public void stopDrag() {
		isMoving = false;
		if (dragImageView != null) {
			windowManager.removeView(dragImageView);
			dragImageView = null;
		}
		isSameDragDirection = true;
		lastFlag = -1; //-1,0 == down,1== up
		DragListAdapter adapter = (DragListAdapter) getAdapter();
		adapter.setLastFlag(lastFlag);
		adapter.pastList();
	}
	
	/**
	 * �϶����µ�ʱ��
	 * 
	 * @param y
	 */
	public void onDrop(int y) {
		onDrop(0, y);
	}
	
	private int holdPosition;
	
	
	public Animation getFromSelfAnimation(int x,int y){
		TranslateAnimation go = new TranslateAnimation(Animation.RELATIVE_TO_SELF, 0, Animation.ABSOLUTE, x, 
				Animation.RELATIVE_TO_SELF, 0, Animation.ABSOLUTE, y);
		go.setInterpolator(new AccelerateDecelerateInterpolator());
		go.setFillAfter(true);
		go.setDuration(ANIMATION_DURATION);	
		go.setInterpolator(new AccelerateInterpolator());
		return go;
	}
	
	public Animation getToSelfAnimation(int x,int y){
		TranslateAnimation go = new TranslateAnimation(
				 Animation.ABSOLUTE, x, Animation.RELATIVE_TO_SELF, 0, 
				 Animation.ABSOLUTE, y, Animation.RELATIVE_TO_SELF, 0);
		go.setInterpolator(new AccelerateDecelerateInterpolator());
		go.setFillAfter(true);
		go.setDuration(ANIMATION_DURATION);	
		go.setInterpolator(new AccelerateInterpolator());
		return go;
	}
	
	public Animation getAbsMoveAnimation(int x,int y){
		TranslateAnimation go = new TranslateAnimation(Animation.RELATIVE_TO_SELF, 0, Animation.ABSOLUTE, x, 
				Animation.RELATIVE_TO_SELF, 0, Animation.ABSOLUTE, y);
		go.setInterpolator(new AccelerateDecelerateInterpolator());
		go.setFillAfter(true);
		go.setDuration(ANIMATION_DURATION);	
		go.setInterpolator(new AccelerateInterpolator());
		return go;
	}
	
	public Animation getAnimation(int fromY,int toY){
		TranslateAnimation go = new TranslateAnimation(Animation.RELATIVE_TO_SELF, 0, Animation.ABSOLUTE, 0, 
				Animation.ABSOLUTE, fromY, Animation.ABSOLUTE, toY);
		go.setInterpolator(new AccelerateDecelerateInterpolator());
		go.setFillAfter(true);
		go.setDuration(ANIMATION_DURATION);	
		go.setInterpolator(new AccelerateInterpolator());
		return go;
	}
	
	public Animation getAbsMoveAnimation2(int x,int y){
		TranslateAnimation go = new TranslateAnimation(Animation.ABSOLUTE, x, Animation.RELATIVE_TO_SELF, 0,
				Animation.ABSOLUTE, y, Animation.RELATIVE_TO_SELF, 0);
		go.setInterpolator(new AccelerateDecelerateInterpolator());
		
		go.setFillAfter(true);
		go.setDuration(ANIMATION_DURATION);	
		go.setInterpolator(new AccelerateInterpolator());
		return go;
	}

}