sap.ui.define([
    "sap/ui/model/xml/XMLListBinding"
], function(XMLListBinding) {
    "use strict";

    var WebdavListBinding = XMLListBinding.extend("medunited.care.model.WebdavListBinding", {
        constructor: function(oModel, sPath, oContext, aSorters, aFilters, mParameters) {
            XMLListBinding.apply(this, arguments);
            this.oModel = oModel;
            this.sPath = sPath;
            this.oContext = oContext;
            this.aSorters = aSorters;
            this.aFilters = aFilters;
            this.mParameters = mParameters || {};

            this.iStartIndex = 0;
            this.iPageSize = mParameters && mParameters.pageSize ? mParameters.pageSize : 15;
            this.bHasMoreData = true;
        },

        /**
         * Load data for the given range from the server.
         *
         * @param {int} iStartIndex The start index
         * @param {int} iLength The amount of data to be requested
         */
        loadData: function() {
            const iStartIndex = this.iStartIndex;
            const iPageSize = this.iPageSize;

            var that = this;

            this.oModel.readWebdavFolder(this.sPath, iStartIndex, iPageSize)
                .then(function(oData) {
                    const aNewData = Array.from(oData.getElementsByTagName("response"));
                    const iTotalItems = parseInt(oData.getElementsByTagName("totalItems")[0]?.textContent || "0", 10);

                    var oDataNode = that.oModel.getObject(that.sPath);
                    if (oDataNode) {
                        aNewData.forEach((node) => oDataNode.appendChild(node.cloneNode(true)));
                    }

                    const iTotalPages = Math.ceil(iTotalItems / iPageSize);
                    that.bHasMoreData = iStartIndex + iPageSize < iTotalItems;

                    that.oModel.refresh(true);
                    that.refresh();

                })
                .catch(function(oError) {
                    console.error("Error loading data from WebDAV folder:", oError);
                });
        },

        nextPage: function() {
            if (this.bHasMoreData) {
                this.iStartIndex += this.iPageSize;
                this.loadData();
            } else {
                console.warn("No more data to load.");
            }
        },
        previousPage: function() {
            if (this.iStartIndex > 0) {
                this.iStartIndex = Math.max(0, this.iStartIndex - this.iPageSize);
                this.loadData();
            } else {
                console.warn("Already at the first page.");
            }
        },
        setPage: function(iPage) {
            const iNewStartIndex = (iPage - 1) * this.iPageSize;
            if (iNewStartIndex >= 0) {
                this.iStartIndex = iNewStartIndex;
                this.loadData();
            } else {
                console.error("Invalid page number:", iPage);
            }
        }
    });

    return WebdavListBinding;
});