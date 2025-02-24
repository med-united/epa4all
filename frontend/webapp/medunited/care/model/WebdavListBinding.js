sap.ui.define([
    "sap/ui/model/xml/XMLListBinding",
	"sap/ui/model/ChangeReason"
], function(XMLListBinding, ChangeReason) {
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
            this.iPageSize = mParameters?.pageSize || 8;
            this.totalCount = 0;
            this.bHasMoreData = true;
			this.bPendingRequest = false;
			this.iLength = 0;
			this.bNeedsUpdate = false;

            console.log("Initializing WebdavListBinding with path:", sPath);

        },
	
       loadData: function() {
		   this.bPendingRequest = true;
		   this.fireDataRequested();
		   this.bNeedsUpdate = true;
           this.oModel.readWebdavFolder("", this.iStartIndex, this.iPageSize, this.aSorters, this.aFilters)
               .then(({ xml, headers }) => {
                   this.oModel.setXML(xml);
                   console.log("heree");
                   console.log(xml);
                   this.totalCount = parseInt(headers.total, 10) || 0;

                   console.log("X-Total-Count from response:", this.totalCount);
                   console.log("Current Start Index:", this.iStartIndex, "Page Size:", this.iPageSize);

                   this.bHasMoreData = (this.iStartIndex + this.iPageSize) < this.totalCount;
                   console.log("Pagination Status -> Has More Data:", this.bHasMoreData, "Has Previous:", this.iStartIndex > 0);
				   this.bPendingRequest = false;

				   
				   let iCurrentPageLength = xml.match(/<D:response>/g).length;
				   
				   // amount of data that was loaded with this request
				   this.iLength += iCurrentPageLength;
				   this.oModel.callAfterUpdate(() =>  {
		   				this.fireDataReceived({data: xml});
		   			});
					this.checkUpdate();
               })
               .catch(error => {
				   this.bPendingRequest = false;
				   console.error("Error loading data:", error);
				
			   });
       }

    });
	
	WebdavListBinding.prototype.getContexts = function(iStartIndex, iLength, iMaximumPrefetchSize,
				bKeepCurrent) {
		let oSkipAndTop = this._getSkipAndTop(iStartIndex, iLength, iMaximumPrefetchSize);

		let aContexts = this._getContexts(iStartIndex, iLength);
		aContexts.bExpectMore = this._isExpectingMoreContexts(aContexts, iStartIndex, iLength);
				
		
		// If rows are missing send a request
		if (!this.bPendingRequest && oSkipAndTop) {
			this.loadData(oSkipAndTop.skip, oSkipAndTop.top);
			aContexts.dataRequested = true;
		}

		return XMLListBinding.prototype.getContexts.apply(this, arguments);
	};
	
	WebdavListBinding.prototype._getSkipAndTop = function(iStartIndex, iLength, iMaximumPrefetchSize) {
		return {
			skip : 0,
			top: 20
		};
	};
	
	WebdavListBinding.prototype.initialize = function() {

		this._fireRefresh({reason: ChangeReason.Refresh});

		// ensure that data state is updated after initialization
		this.checkDataState();

		return this;
	};
	
	/**
	 * Check whether this Binding would provide new values and in case it changed,
	 * inform interested parties about this.
	 *
	 * @param {boolean} [bForceUpdate] Force control update
	 * @private
	 */
	WebdavListBinding.prototype.checkUpdate = function (bForceUpdate) {
		this._fireChange({reason: ChangeReason.Change});
	};

    return WebdavListBinding;
});