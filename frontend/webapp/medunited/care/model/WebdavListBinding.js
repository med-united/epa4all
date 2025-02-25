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
			// This is the amount of data that has already
			// been requested from the server
			this.iLoadedUntilIndex = 0;
            this.iPageSize = mParameters?.pageSize || oModel.iSizeLimit;
            this.totalCount = undefined;
            this.bHasMoreData = true;
			this.bPendingRequest = false;
			// The amount of data that has been loaded from the server
			this.iLength = 0;
			this.bNeedsUpdate = false;
			this.bLengthFinal = false;

            console.log("Initializing WebdavListBinding with path:", sPath);

        },
	
       loadData: function(skip, top) {
		   this.bPendingRequest = true;
		   this.fireDataRequested();
		   this.bNeedsUpdate = true;

		   this.oModel.callAfterUpdate(() =>  {
		   		this.fireDataReceived({
		   		data: {
		   			__count: this.iLength
		   		}
		   	});
		   });
           this.oModel.readWebdavFolder("", skip ? skip : this.iStartIndex, top ? top : this.iPageSize, this.aSorters, this.aFilters)
               .then((oResponse) => {
				   // if we do not have a response just return
				   if(!oResponse) {
						return;
				   }
				   let { xml, headers } = oResponse;
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
				   if(this.iLength >= this.totalCount) {
				 	  this.bLengthFinal = true;
				   }
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
		
		this.iLastThreshold = iMaximumPrefetchSize;
	
		// If rows are missing send a request
		if (!this.bPendingRequest && this.iLoadedUntilIndex < iLength) {
			this.loadData(this.iLoadedUntilIndex, this.iPageSize);
			this.iLoadedUntilIndex = this.iLoadedUntilIndex + this.iPageSize;
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
		}
		if (this.totalCount > 0 && this.totalCount < iLength) {
			iLength = this.totalCount;
		}
		// if we don't know the total count
		// set the length to the amount of responses in the model
		if(this.totalCount === undefined && this.oModel.oData) {
			iLength = this.oModel.oData.querySelectorAll("response").length;
		}
		if(this.totalCount === undefined && this.oModel.oData === undefined) {
			// there is no count and the model is not initialized
			return [];
		}

		//	Loop through known data and check whether we already have all rows loaded
		for (var i = iStartIndex; i < iLength; i++) {
			// do not return more context than we have loaded from the server
			oContext = this.oModel.getContext('/response/' + i);
			aContexts.push(oContext);
		}

		return aContexts;
	};
	
	
	WebdavListBinding.prototype.initialize = function() {

		this._fireRefresh({reason: ChangeReason.Refresh});

		// ensure that data state is updated after initialization
		this.checkDataState();

		return this;
	};
	
	WebdavListBinding.prototype.isLengthFinal = function() {
		return this.bLengthFinal;
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