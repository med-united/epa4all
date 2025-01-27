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
        },

        /**
         * Load data for the given range from the server.
         *
         * @param {int} iStartIndex The start index
         * @param {int} iLength The amount of data to be requested
         */
        loadData: function (iStartIndex, iLength) {
            var that = this;

            this.oModel.readWebdavFolder(this.sPath, iStartIndex, iLength)
                .then(function (oData) {
                    var existingData = that.oModel.getProperty(that.sPath) || [];
                    var newData = Array.from(oData.getElementsByTagName("response"));

                    existingData = existingData.concat(newData);

                    that.oModel.setProperty(that.sPath, existingData);
                    that.refresh();
                })
                .catch(function (oError) {
                    console.error("Error loading data from WebDAV folder:", oError);
                });
        };

    });

    return WebdavListBinding;
});
