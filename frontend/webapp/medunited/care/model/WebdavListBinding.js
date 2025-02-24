sap.ui.define([
    "sap/ui/model/xml/XMLListBinding"
], function(XMLListBinding) {
    "use strict";

    var WebdavListBinding = XMLListBinding.extend("medunited.care.model.WebdavListBinding", {
        constructor: function(oModel, sPath, oContext, aSorters, aFilters, mParameters) {
            XMLListBinding.apply(this, arguments);
            this.oModel = oModel;
            this.sPath = "";
            this.oContext = oContext;
            this.aSorters = aSorters;
            this.aFilters = aFilters;
            this.mParameters = mParameters || {};
            this.iStartIndex = 0;
            this.iPageSize = mParameters?.pageSize || 8;
            this.totalCount = 0;
            this.bHasMoreData = true;

            console.log("Initializing WebdavListBinding with path:", sPath);

            this.loadData();
        },

       loadData: function() {
           this.oModel.readWebdavFolder(this.sPath, this.iStartIndex, this.iPageSize, this.aSorters, this.aFilters)
               .then(({ xml, headers }) => {
                   this.oModel.setXML(xml);
                   console.log("heree");
                   console.log(xml);
                   this.totalCount = parseInt(headers.total, 10) || 0;

                   console.log("X-Total-Count from response:", this.totalCount);
                   console.log("Current Start Index:", this.iStartIndex, "Page Size:", this.iPageSize);

                   this.bHasMoreData = (this.iStartIndex + this.iPageSize) < this.totalCount;
                   console.log("Pagination Status -> Has More Data:", this.bHasMoreData, "Has Previous:", this.iStartIndex > 0);

                   this.refresh();

                   const oPageModel = sap.ui.getCore().byId("__xmlview0").getModel("pageModel");
                   if (oPageModel) {
                       oPageModel.setProperty("/hasNext", this.bHasMoreData);
                       oPageModel.setProperty("/hasPrevious", this.iStartIndex > 0);
                       oPageModel.refresh(true);
                   }
               })
               .catch(error => console.error("Error loading data:", error));
       },



        nextPage: function() {
            if (this.bHasMoreData) {
                this.iStartIndex += this.iPageSize;
                this.loadData();
            }
        },

        previousPage: function() {
            if (this.iStartIndex > 0) {
                this.iStartIndex = Math.max(0, this.iStartIndex - this.iPageSize);
                this.loadData();
            }
        }


    });

    return WebdavListBinding;
});