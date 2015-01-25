package jp.egg.android.view.widget.gadget;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import jp.egg.android.R;
import jp.egg.android.manager.FontAwesome;
import jp.egg.android.manager.IconFontManager;
import jp.egg.android.util.Log;

/**
 * Created by chikara on 2014/10/10.
 */
public class FontAwesomeTextView extends IconFontView {

    private static final String TAG = FontAwesomeTextView.class.getSimpleName();

    public static final String ASSETS_PATH = "fontawesome-webfont.ttf";

    public FontAwesomeTextView(Context context) {
        super(context);
        setUp(context, null);
    }

    public FontAwesomeTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setUp(context, attrs);
    }

    public FontAwesomeTextView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        setUp(context, attrs);
    }

    private void setUp(Context context, AttributeSet attrs){

        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.FontAwesomeTextView);
        String iconName = null;
        if( a.hasValue(R.styleable.FontAwesomeTextView_faIconName) ) {
            iconName = a.getString(R.styleable.FontAwesomeTextView_faIconName);
        }
        a.recycle();

        if (iconName!=null) {
            setTextByIconName(iconName);
        }

    }

    public void setTextByIconName (String iconName) {
        String text = iconName!=null ?
                FontAwesome.getFaMap().get(iconName) :
                null;
        setText(text!=null ? text : "");
    }

    @Override
    protected String onCustomFontSetUp() {
        return ASSETS_PATH;
    }
}
