package com.rf.taskmodule.ui.category

data class AddCategoryResponse(
    var cid: String?,
    var loginToken: String?,
    var responseCode: String?,
    var responseMsg: String?,
    var successful: Boolean
)