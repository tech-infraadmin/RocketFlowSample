package com.rf.taskmodule.ui.category

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.rocketflow.sdk.RocketFlyer
import com.rf.taskmodule.BR
import com.rf.taskmodule.R
import com.rf.taskmodule.TrackiSdkApplication
import com.rf.taskmodule.data.local.prefs.PreferencesHelper
import com.rf.taskmodule.data.model.BaseResponse
import com.rf.taskmodule.data.network.APIError
import com.rf.taskmodule.data.network.ApiCallback
import com.rf.taskmodule.data.network.HttpManager
import com.rf.taskmodule.databinding.ActivityProductCategoryListSdkBinding
import com.rf.taskmodule.ui.base.BaseSdkActivity
import com.rf.taskmodule.ui.common.DoubleButtonDialog
import com.rf.taskmodule.ui.common.OnClickListener
import com.rf.taskmodule.ui.selectorder.CataLogProductCategory
import com.rf.taskmodule.ui.selectorder.CatalogCategoryResponse
import com.rf.taskmodule.ui.selectorder.PaginationRequest
import com.rf.taskmodule.ui.tasklisting.PaginationListener
import com.rf.taskmodule.utils.ApiType
import com.rf.taskmodule.utils.AppConstants
import com.rf.taskmodule.utils.CommonUtils
import com.rf.taskmodule.utils.JSONConverter
import java.util.HashMap

class ProductCategoryListActivity :
    com.rf.taskmodule.ui.base.BaseSdkActivity<ActivityProductCategoryListSdkBinding, GetFlavourCategoryListViewModel>(),
    ProductCategoryNavigator, CategoryAdapter.OnCategoryListener {
    private var subCat: Boolean=false
    private var categoryMap: String? = null
    private var position: Int? = null

    //@Inject
    lateinit var getFlavourCategoryListViewModel: GetFlavourCategoryListViewModel


    lateinit var mPref: com.rf.taskmodule.data.local.prefs.PreferencesHelper
    lateinit var httpManager: com.rf.taskmodule.data.network.HttpManager

    lateinit var binding: ActivityProductCategoryListSdkBinding

    lateinit var categoryAdapter: CategoryAdapter

    private var mMapCategory: Map<String, String>? = null
    var flavorId: String? = null

    private var currentPage = com.rf.taskmodule.ui.tasklisting.PaginationListener.PAGE_START
    private var isLastPage = false
    private var isLoading = false
    private var mLayoutManager: LinearLayoutManager? = null
    private var rvProductCategory: RecyclerView? = null
    private var paginationRequest: PaginationRequest? = null
    private var isDelete = false
    private var cid: String? = null

    companion object {
        private val TAG = ProductCategoryListActivity::class.java.simpleName
        fun newIntent(context: Context) = Intent(context, ProductCategoryListActivity::class.java)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = viewDataBinding
        getFlavourCategoryListViewModel.navigator = this
        categoryAdapter = CategoryAdapter(this)

        httpManager = RocketFlyer.httpManager()!!
        mPref = RocketFlyer.preferenceHelper()!!


        if (intent.hasExtra("subCat")) {
             subCat = intent.getBooleanExtra("subCat", false)
            var subCatName = ""
            if (intent.hasExtra("subCatName")) {
                subCatName = intent.getStringExtra("subCatName")!!
            }
            if (intent.hasExtra("cid")) {
                cid = intent.getStringExtra("cid")!!
            }
            if (subCat) {
                setToolbar(binding.toolbar, "${subCatName} Category")
            }
        } else {
            setToolbar(binding.toolbar, "Product Categories")
        }
        if (intent.hasExtra(com.rf.taskmodule.utils.AppConstants.Extra.EXTRA_CATEGORIES)) {
            categoryMap = intent.getStringExtra(com.rf.taskmodule.utils.AppConstants.Extra.EXTRA_CATEGORIES)
            com.rf.taskmodule.utils.CommonUtils.showLogMessage("e", "categoryMap", categoryMap)
            mMapCategory = Gson().fromJson<Map<String, String>>(
                categoryMap,
                object : TypeToken<HashMap<String?, String?>?>() {}.type
            )
            if (mMapCategory != null) {
                if (mMapCategory!!.containsKey("flavorId")) {
                    flavorId = mMapCategory!!["flavorId"]
                }
            }
        }
        binding.ivAddCategory.setOnClickListener {
            addNewCategory()
        }
        getCategories()

    }

    override fun getBindingVariable(): Int {
        return BR.viewModel
    }

    override fun getLayoutId(): Int {
        return R.layout.activity_product_category_list_sdk
    }


    override fun getViewModel(): GetFlavourCategoryListViewModel {
        val factory = RocketFlyer.dataManager()?.let { GetFlavourCategoryListViewModel.Factory(it) } // Factory
        if (factory != null) {
            getFlavourCategoryListViewModel = ViewModelProvider(this, factory)[GetFlavourCategoryListViewModel::class.java]
        }
        return getFlavourCategoryListViewModel!!
    }


    override fun onCategorySelected(prodCat: CataLogProductCategory) {
        isDelete = false
        if (prodCat.subCat!!) {
            val categoryIntent = Intent(this, ProductCategoryListActivity::class.java)
            categoryIntent.putExtra(
                com.rf.taskmodule.utils.AppConstants.Extra.EXTRA_CATEGORIES,
                categoryMap
            )
            categoryIntent.putExtra(
                "subCat",
                true
            )
            categoryIntent.putExtra("cid", prodCat.cid)
            categoryIntent.putExtra("subCatName", prodCat.name)
            startActivity(categoryIntent)
        } else {

        }
    }

    var CONST_ADD = 4567

    override fun onEditProduct(product: CataLogProductCategory, position: Int) {
        isDelete = false
        this.position = position
        var intent = Intent(this, AddProductCategoryActivity::class.java)
        intent.putExtra("action", "edit")
        intent.putExtra("data", product)
        if (categoryMap != null) {
            intent.putExtra(
                com.rf.taskmodule.utils.AppConstants.Extra.EXTRA_CATEGORIES,
                categoryMap
            )
        }
        startActivityForResult(intent, CONST_ADD)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (resultCode) {
            Activity.RESULT_OK -> {
                if (requestCode == CONST_ADD) {
                    currentPage = com.rf.taskmodule.ui.tasklisting.PaginationListener.PAGE_START
                    getCategories()
                }

            }
            Activity.RESULT_CANCELED -> {

            }
        }

    }

    override fun onStatusChange(product: CataLogProductCategory, position: Int) {
        if (com.rf.taskmodule.TrackiSdkApplication.getApiMap().containsKey(ApiType.UPDATE_PRODUCT_CATEGORY_STATUS)) {
            this.position = position
            isDelete = false
            showLoading()
            getFlavourCategoryListViewModel.updateStatusProductCategory(httpManager, product)
        }
    }

    override fun onDeleteCategory(prodCat: CataLogProductCategory, position: Int) {
        val dialog = DoubleButtonDialog(this,
            true,
            null,
            getString(R.string.delete_category),
            getString(R.string.yes),
            getString(R.string.no),
            object : OnClickListener {
                override fun onClickCancel() {}
                override fun onClick() {
                    this@ProductCategoryListActivity.position = position
                    if (com.rf.taskmodule.TrackiSdkApplication.getApiMap().containsKey(ApiType.DELETE_PRODUCT_CATEGORY)) {
                        isDelete = true
                        showLoading()
                        getFlavourCategoryListViewModel.deleteProductCategory(httpManager, prodCat)
                    }
                }
            })
        dialog.show()
    }

    override fun addNewCategory() {
        val categoryIntent =Intent(this,AddProductCategoryActivity::class.java)
        categoryIntent.putExtra("action", "Add")
        if (categoryMap != null) {
            categoryIntent.putExtra(
                com.rf.taskmodule.utils.AppConstants.Extra.EXTRA_CATEGORIES,
                categoryMap
            )
        }
        if(subCat&&cid!=null)
        {
            categoryIntent.putExtra("parentCategoryId", cid)
        }
        startActivityForResult(categoryIntent, CONST_ADD)
    }

    override fun handleResponse(callback: com.rf.taskmodule.data.network.ApiCallback, result: Any?, error: APIError?) {
    }

    override fun handleProductCategoryResponse(
        callback: com.rf.taskmodule.data.network.ApiCallback,
        result: Any?,
        error: APIError?
    ) {

        hideLoading()
        this.isLoading = false
        if (com.rf.taskmodule.utils.CommonUtils.handleResponse(callback, error, result, this)) {
            var jsonConverter =
                com.rf.taskmodule.utils.JSONConverter<CatalogCategoryResponse>()
            var responseMain: CatalogCategoryResponse = jsonConverter.jsonToObject(
                result.toString(),
                CatalogCategoryResponse::class.java
            ) as CatalogCategoryResponse
            if (responseMain.data != null && responseMain.data!!.isNotEmpty()) {
                var list = responseMain.data!!

                setRecyclerView()
                categoryAdapter!!.addItems(list)
                binding.ivAddCategory.visibility = View.VISIBLE
                com.rf.taskmodule.utils.CommonUtils.showLogMessage(
                    "e", "adapter total_count =>",
                    "" + categoryAdapter.itemCount
                )
                com.rf.taskmodule.utils.CommonUtils.showLogMessage(
                    "e", "fetch total_count =>",
                    "" + responseMain.count
                )
                isLastPage = categoryAdapter.itemCount >= responseMain.count

            } else {
                binding.ivAddCategory.visibility = View.GONE
                setRecyclerView()
                categoryAdapter!!.addItems(ArrayList())
            }
        }
    }

    override fun handleDeleteProductCategoryResponse(
        callback: com.rf.taskmodule.data.network.ApiCallback,
        result: Any?,
        error: APIError?
    ) {
        hideLoading()
        if (com.rf.taskmodule.utils.CommonUtils.handleResponse(callback, error, result, this)) {
            var jsonConverter =
                com.rf.taskmodule.utils.JSONConverter<BaseResponse>()
            var responseMain: BaseResponse? = jsonConverter.jsonToObject(
                result.toString(),
                BaseResponse::class.java
            )
            if (responseMain != null && responseMain.successful) {
                if (!isDelete) {

                } else {
                    categoryAdapter!!.removeAt(position!!, categoryAdapter!!.getAllList().size)
                }

                if (categoryAdapter!!.getAllList().isEmpty()) {
                    binding.ivAddCategory.visibility = View.GONE
                }
            }
        }

    }

    override fun handleUpdateProductStatusCategoryResponse(
        callback: com.rf.taskmodule.data.network.ApiCallback,
        result: Any?,
        error: APIError?
    ) {
        hideLoading()
        if (com.rf.taskmodule.utils.CommonUtils.handleResponse(callback, error, result, this)) {
            var jsonConverter =
                com.rf.taskmodule.utils.JSONConverter<BaseResponse>()
            var responseMain: BaseResponse? = jsonConverter.jsonToObject(
                result.toString(),
                BaseResponse::class.java
            )
            if (responseMain != null && responseMain.successful) {
                if (!isDelete) {

                } else {
                    categoryAdapter!!.removeAt(position!!, categoryAdapter!!.getAllList().size)
                }

                if (categoryAdapter!!.getAllList().isEmpty()) {
                    binding.ivAddCategory.visibility = View.GONE
                }
            }
        }

    }


    private fun getCategories() {
        if (currentPage == com.rf.taskmodule.ui.tasklisting.PaginationListener.PAGE_START) {
            if (::categoryAdapter.isInitialized)
                categoryAdapter.clearList()
            if (rvProductCategory != null)
                rvProductCategory!!.removeAllViewsInLayout()
        }
        if (::categoryAdapter.isInitialized && categoryAdapter.getAllList().isEmpty()) {
            showLoading()
        }
        paginationRequest = PaginationRequest()
        paginationRequest!!.limit = 10
        paginationRequest!!.offset = (currentPage - 1) * 10
        paginationRequest!!.dataCount = 10
        getFlavourCategoryListViewModel.getProductCategory(
            httpManager,
            paginationRequest!!,
            flavorId,
            cid
        )
    }

    private fun setRecyclerView() {
        if (rvProductCategory == null) {
            rvProductCategory = binding.rvProductCategory
            rvProductCategory!!.adapter = categoryAdapter
            //  mLayoutManager= (LinearLayoutManager) rvAttendance.getLayoutManager();
            try {
                mLayoutManager = LinearLayoutManager(this)
                mLayoutManager!!.orientation = RecyclerView.VERTICAL
                rvProductCategory!!.layoutManager = mLayoutManager
                rvProductCategory!!.itemAnimator = DefaultItemAnimator()

            } catch (e: IllegalArgumentException) {
            }

        }

        rvProductCategory!!.addOnScrollListener(object : com.rf.taskmodule.ui.tasklisting.PaginationListener(mLayoutManager!!) {
            override fun loadMoreItems() {
                this@ProductCategoryListActivity.isLoading = true
                currentPage++
                getCategories()
            }

            override fun isLastPage(): Boolean {
                return this@ProductCategoryListActivity.isLastPage
            }

            override fun isLoading(): Boolean {
                return this@ProductCategoryListActivity.isLoading
            }
        })
    }
}