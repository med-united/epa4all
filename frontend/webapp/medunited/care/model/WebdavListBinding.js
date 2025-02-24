sap.ui.define([
    "sap/ui/model/ListBinding",
	"sap/ui/model/ChangeReason"
], function(ListBinding, ChangeReason) {
    "use strict";

    var WebdavListBinding = ListBinding.extend("medunited.care.model.WebdavListBinding", {
        constructor: function(oModel, sPath, oContext, aSorters, aFilters, mParameters) {
            ListBinding.apply(this, arguments);
            this.oModel = oModel;
            this.sPath = sPath;
            this.oContext = oContext;
            this.aSorters = aSorters;
            this.aFilters = aFilters;
            this.mParameters = mParameters || {};
            this.iStartIndex = 0;
            this.iPageSize = mParameters?.pageSize || oModel.iSizeLimit;
            this.totalCount = undefined;
            this.bHasMoreData = true;
			this.bPendingRequest = false;
			this.iLength = 0;
			this.bNeedsUpdate = false;
			this.bLengthFinal = false;

            console.log("Initializing WebdavListBinding with path:", sPath);

        },
	
       loadData: function(skip, top) {
		   this.bPendingRequest = true;
		   this.fireDataRequested();
		   this.bNeedsUpdate = true;
           this.oModel.readWebdavFolder("", skip ? skip : this.iStartIndex, top ? top : this.iPageSize, this.aSorters, this.aFilters)
               .then(({ xml, headers }) => {
                   // this.oModel.setXML(xml);
                   console.log("heree");
                   console.log(xml);
                   this.totalCount = parseInt(headers.total, 10) || 0;

                   console.log("X-Total-Count from response:", this.totalCount);
                   console.log("Current Start Index:", skip, "Page Size:", top ? top : this.iPageSize);

                   this.bHasMoreData = (skip + (top ? top : this.iPageSize)) < this.totalCount;
                   console.log("Pagination Status -> Has More Data:", this.bHasMoreData, "Has Previous:", this.iStartIndex > 0);
				   this.bPendingRequest = false;

				   
				   let iCurrentPageLength = !xml ? 0 : xml.match(/<D:response>/g).length;
				   if(iCurrentPageLength != 0) {					
				   	  this.bNeedsUpdate = true;
				   }
				   
				   // amount of data that was loaded with this request
				   this.iLength += iCurrentPageLength;
				   this.oModel.callAfterUpdate(() =>  {
		   				this.fireDataReceived({
							data: {
								__count:this.iLength,
								results:xml
							}
						});
		   			});
					this.checkUpdate();
               })
               .catch(error => {
				   this.bPendingRequest = false;
				   console.error("Error loading data:", error);
				
			   });
       }

    });
	
	WebdavListBinding.prototype.getLength = function() {
		// If length is not final and larger than zero, add some additional length to enable
		// scrolling/paging for controls that only do this if more items are available
		if (this.bLengthFinal || this.iLength == 0) {
			return this.iLength;
		} else {
			var iAdditionalLength = this.iLastThreshold || this.iLastLength || 10;
			return this.iLength + iAdditionalLength;
		}
	};
	
	WebdavListBinding.prototype.getContexts = function(iStartIndex, iLength, iMaximumPrefetchSize,
				bKeepCurrent) {
		
		this.iLastLength = iLength;
		if(iStartIndex) {			
			this.iLastStartIndex = iStartIndex;
		} else if(this.iLastStartIndex === undefined) {
			this.iLastStartIndex = 0;
		} else {
			this.iLastStartIndex += this.iPageSize;
		}
		iStartIndex = this.iLastStartIndex;
		this.iLastThreshold = iMaximumPrefetchSize;
					
		let oSkipAndTop = this._getSkipAndTop(iStartIndex, iLength, iMaximumPrefetchSize);

		let aContexts = this._getContexts(iStartIndex, iLength);
		aContexts.bExpectMore = this._isExpectingMoreContexts(aContexts, iStartIndex, iLength);
				
		
		// If rows are missing send a request
		if (!this.bPendingRequest && oSkipAndTop && (this.totalCount === undefined || this.totalCount < (oSkipAndTop.skip + oSkipAndTop.top))) {
			this.loadData(oSkipAndTop.skip, oSkipAndTop.top);
			aContexts.dataRequested = true;
		}

		return this._getContexts(iStartIndex, iLength);
	};
	
	/**
	 * Return contexts for the list
	 *
	 * @param {int} [iStartIndex=0] the start index of the requested contexts
	 * @param {int} [iLength] the requested amount of contexts
	 *
	 * @return {Array} the contexts array
	 * @private
	 */
	WebdavListBinding.prototype._getContexts = function(iStartIndex, iLength) {
		var aContexts = [],
			oContext;

		if (!iStartIndex) {
			iStartIndex = 0;
		}
		if (!iLength) {
			iLength = this.oModel.iSizeLimit;
			if (this.bLengthFinal && this.iLength < iLength) {
				iLength = this.iLength;
			}
		}

		//	Loop through known data and check whether we already have all rows loaded
		for (var i = iStartIndex; i < iStartIndex + iLength; i++) {
			oContext = this.oModel.getContext('/response/' + i);
			aContexts.push(oContext);
		}

		return aContexts;
	};
	
	WebdavListBinding.prototype._getSkipAndTop = function(iStartIndex, iLength, iMaximumPrefetchSize) {
		return {
			skip : iStartIndex,
			top: !iLength ? this.pageSize :iLength
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
		if(bForceUpdate || this.bNeedsUpdate) {
			this.bNeedsUpdate = false;
			this._fireChange({reason: ChangeReason.Change});
		}
	};

    return WebdavListBinding;
});