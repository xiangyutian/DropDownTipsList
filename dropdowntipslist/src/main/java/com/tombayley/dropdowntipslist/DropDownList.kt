package com.tombayley.dropdowntipslist

import android.animation.ValueAnimator
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import java.lang.Exception
import java.util.*

class DropDownList(context: Context, attrs: AttributeSet): LinearLayout(context, attrs) {

    // Preferences are used to store the:
    //  - time an item was first added to the list
    //  - whether the item has been dismissed
    lateinit var preferences: SharedPreferences

    // Views
    protected var listContainer: LinearLayout
    protected var arrow: ImageView
    protected var headerTitlePrefix: TextView
    protected var headerTitle: TextView
    protected var numTips: TextView
    protected var header: ViewGroup

    // State
    protected var isExpanded: Boolean = false
    protected var mCurrentKey = 0
    protected var mListItemKeys = LinkedHashMap<Int, Int>()

    protected var primaryTextColor: Int = 0
    protected var primaryTextColorFaded: Int = 0
    protected var accentColor: Int = 0

    // If false, drop down behaves normally
    // If true, the droop down list header is hidden and is permanently expanded
    //  - Useful for showing a list of all tips
    protected var showAllExpanded: Boolean = false

    var keepSpaceIfEmpty: Boolean = false
    set(value) {
        field = value
        hideView()
    }

    companion object {
        // Default key return value if an item was not added to the list
        const val ITEM_NOT_ADDED = -1
    }

    init {
        inflate(context, R.layout.drop_down_list, this)

        listContainer = findViewById(R.id.list_container)
        arrow = findViewById(R.id.arrow)
        headerTitlePrefix = findViewById(R.id.header_title_prefix)
        headerTitle = findViewById(R.id.header_title)
        numTips = findViewById(R.id.num_tips)
        header = findViewById(R.id.header)

        headerTitle.isSelected = true
        header.setOnClickListener {
            if (isExpanded) {
                collapse()
            } else {
                expand()
            }

            isExpanded = !isExpanded
        }

        val attributes = context.obtainStyledAttributes(attrs, R.styleable.DropDownList)
        primaryTextColor = attributes.getColor(R.styleable.DropDownList_attr_primaryTextColor, Color.BLACK)
        primaryTextColorFaded = ColorUtil.giveColorAlpha(primaryTextColor, 0.6f)
        accentColor = attributes.getColor(R.styleable.DropDownList_attr_accentColor, Color.BLUE)
        showAllExpanded = attributes.getBoolean(R.styleable.DropDownList_attr_showAllExpanded, false)
        keepSpaceIfEmpty = attributes.getBoolean(R.styleable.DropDownList_attr_keepSpaceIfEmpty, false)
        attributes.recycle()

        // Hide the drop down list as it is empty
        hideView()

        headerTitlePrefix.setTextColor(primaryTextColor)
        headerTitle.setTextColor(primaryTextColor)
        numTips.setTextColor(accentColor)
        ColorUtil.setImageColor(arrow, primaryTextColorFaded)

        if (showAllExpanded) {
            listContainer.layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
            header.visibility = View.GONE
        }
    }

    /**
     * Adds all items in the provided list to the drop down list
     *
     * @param items List<Item>
     */
    public fun addAll(items: List<Item>) {
        for (item in items) {
            addItem(item)
        }
    }

    /**
     * Adds an item to the drop down list
     *
     * @param item Item
     * @return Int Key of the item added. Can be used to remove the item manually later
     */
    public fun addItem(item: Item): Int {
        if (!isItemValid(item)) return ITEM_NOT_ADDED

        if (!showAllExpanded) {
            if (item.appearAfterHours != Item.UNDEFINED) {
                if (!isAfterHours(item.appearAfterHours.toLong(), item.prefKey!!, item.beginTime)) {
                    return ITEM_NOT_ADDED
                }
            }
            if (preferences.getBoolean(item.hasDismissedKey, false)) {
                return ITEM_NOT_ADDED
            }
        }

        val itemKey = ++mCurrentKey
        mListItemKeys[itemKey] = listContainer.childCount

        if (listContainer.childCount == 0) {
            headerTitle.text = item.title
        }

        numTips.text = itemKey.toString()

        visibility = View.VISIBLE

        val prevHeight = getListMeasuredHeight()

        listContainer.addView(inflateItem(
            item,
            Runnable { removeItem(itemKey, item) }
        ))

        val newHeight = getListMeasuredHeight()
        if (isExpanded) {
            animateHeightChange(prevHeight, newHeight)
        }

        return itemKey
    }

    /**
     * Checks if an Item has been correctly initialised
     *
     * @param item Item
     * @return Boolean
     */
    protected fun isItemValid(item: Item): Boolean {
        if (
            item.beginTime == Item.UNDEFINED.toLong()
            || item.appearAfterHours == Item.UNDEFINED
            || item.prefKey == null
        ) {
            throw Exception("setAppearAfter() must be called when creating the Item")
        }

        return true
    }

    /**
     * Removes an item from the drop down list with a given key
     *
     * @param key Int
     * @param item Item
     */
    public fun removeItem(key: Int, item: Item) {
        if (!mListItemKeys.containsKey(key)) return
        val position: Int? = mListItemKeys[key]
        position ?: return

        val prevHeight = getListMeasuredHeight()
        listContainer.removeViewAt(position)
        reCalcListPositions(position)

        if (!showAllExpanded) {
            preferences.edit().putBoolean(item.hasDismissedKey, true).apply()
        }

        val newNumListItems = listContainer.childCount

        if (newNumListItems >= 1) {
            headerTitle.text = (listContainer.getChildAt(0).findViewById(R.id.title) as TextView).text
        } else {
            headerTitle.text = ""
            hideView()
        }

        numTips.text = newNumListItems.toString()

        if (isExpanded) animateHeightChange(prevHeight, getListMeasuredHeight())
    }

    protected fun hideView() {
        visibility = if (keepSpaceIfEmpty) View.INVISIBLE else View.GONE
    }

    /**
     * Expands the drop down list
     */
    public fun expand() {
        headerTitle.visibility = View.INVISIBLE
        numTips.visibility = View.INVISIBLE
        animateHeightChange(0, getListMeasuredHeight())
        arrow.setImageResource(R.drawable.ic_arrow_up)
    }

    /**
     * Collapses the drop down list
     */
    public fun collapse() {
        headerTitle.visibility = View.VISIBLE
        numTips.visibility = View.VISIBLE
        animateHeightChange(getListMeasuredHeight(), 0)
        arrow.setImageResource(R.drawable.ic_arrow_down)
    }

    /**
     * Animates the dropdown list height
     *
     * @param from Int
     * @param to Int
     */
    protected fun animateHeightChange(from: Int, to: Int) {
        val anim = ValueAnimator.ofInt(from, to)
        anim.addUpdateListener { valueAnimator ->
            listContainer.layoutParams.height = valueAnimator.animatedValue as Int
            listContainer.requestLayout()
        }
        anim.duration = 500
        anim.interpolator = FastOutSlowInInterpolator()
        anim.start()
    }

    protected fun getListMeasuredHeight(): Int {
        listContainer.measure(
            MeasureSpec.makeMeasureSpec(listContainer.width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        )
        return listContainer.measuredHeight
    }

    /**
     * Used to check if an Item should be shown if it has passed it's appearAfterHours
     *
     * @param hoursAfter Long
     * @param key String
     * @param beginTime Long Time to start "countdown" to show Item. Typically app install time
     * @return Boolean
     */
    protected fun isAfterHours(hoursAfter: Long, key: String, beginTime: Long): Boolean {
        val savedTimeStart = preferences.getLong(key, 0)
        val currentTime = System.currentTimeMillis()

        if (savedTimeStart == 0L) preferences.edit().putLong(key, currentTime).apply()

        // Hour in millis
        val hour = 60 * 60 * 1000

        return currentTime - beginTime > hoursAfter * hour
    }

    /**
     * Updates the positions of each Item in the drop down list after an item is removed
     *
     * @param positionRemoved Int
     */
    protected fun reCalcListPositions(positionRemoved: Int) {
        if (listContainer.childCount == 0) return

        for (entry in mListItemKeys.entries) {
            val entryPosition = entry.value
            if (entryPosition >= positionRemoved) entry.setValue(entryPosition - 1)
        }
    }

    /**
     * Inflates an Item view
     *
     * @param item Item
     * @param dismissRunnable Runnable
     * @return View
     */
    protected fun inflateItem(item: Item, dismissRunnable: Runnable): View {
        val dropDownListItem = View.inflate(context, R.layout.drop_down_list_item, null) as ViewGroup

        val title: TextView = dropDownListItem.findViewById(R.id.title)
        val description: TextView = dropDownListItem.findViewById(R.id.description)
        val actionTv: TextView = dropDownListItem.findViewById(R.id.action)
        val dismissBtn: ImageView = dropDownListItem.findViewById(R.id.dismiss_item)

        title.text = item.title
        description.text = item.description
        actionTv.text = item.actionText

        title.setTextColor(primaryTextColor)
        description.setTextColor(primaryTextColorFaded)
        ColorUtil.setImageColor(dismissBtn, primaryTextColorFaded)
        actionTv.setTextColor(accentColor)

        if (showAllExpanded) {
            dismissBtn.visibility = View.GONE
        } else {
            dismissBtn.setOnClickListener { dismissRunnable.run() }
        }

        if (item.actionText == null) actionTv.visibility = View.GONE

        if (item.action != null) {
            actionTv.setOnClickListener {
                if (!showAllExpanded) dismissRunnable.run()
                item.action.run()
            }
        }

        return dropDownListItem
    }

    /**
     *
     * @property title String? The Item title text
     * @property description String? The Item description text
     * @property actionText String? The Item action button text
     * @property action Runnable? Runs when the action button is pressed
     *
     * @property beginTime Long The initial time to start "counting down" from. Typically the app install time
     * @property appearAfterHours Int The Item will appear after this many hours have passed the beginTime
     * @property prefKey String? Used to store the time the Item was first (ever) added
     * @property hasDismissedKey String? Used to store if the Item has previously been dismissed
     * @constructor
     */
    class Item(
            val title: String? = null,
            val description: String? = null,
            val actionText: String? = null,
            val action: Runnable? = null
    ) {

        var beginTime: Long = UNDEFINED.toLong()
        var appearAfterHours: Int = UNDEFINED
        var prefKey: String? = null
        var hasDismissedKey: String? = null

        companion object {
            const val UNDEFINED = -1
        }

        /**
         * Separated to make things cleaner if using with Java
         *
         * @param beginTime Long The initial time to start "counting down" from. Typically the app install time
         * @param appearAfterHours Int The Item will appear after this many hours have passed the beginTime
         * @param prefKey String Used to store the time the Item was first (ever) added
         */
        fun setAppearAfter(beginTime: Long, appearAfterHours: Int, prefKey: String) {
            this.beginTime = beginTime
            this.appearAfterHours = appearAfterHours
            this.prefKey = prefKey
            this.hasDismissedKey = prefKey + "_has_shown"
        }
    }

}
