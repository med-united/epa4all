/*!
 * SAP SE
 * (c) Copyright 2009-2022 SAP SE or an SAP affiliate company.
 * Licensed under the Apache License, Version 2.0 - see LICENSE.txt.
 */
sap.ui.define(["sap/ui/model/FilterProcessor","sap/ui/model/Filter","sap/fhir/model/r4/FHIRFilterOperator","sap/base/Log"],function(e,t,r,i){"use strict";var a={};a._evaluateFilter=function(t,r,i){var a,n;e._normalizeCache={true:{},false:{}};if(t.aFilters){return this._evaluateMultiFilter(t,r,i)}a=i(r,t.sPath);n=this.getFilterFunction(t);if(!t.fnCompare||t.bCaseSensitive!==undefined){a=e.normalizeFilterValue(a,t.bCaseSensitive)}return n(a)};a.getFilterFunction=function(t){if(t.sOperator===r.Missing){return function(e,t){return e===t}}else{return e.getFilterFunction(t)}};a._evaluateMultiFilter=function(e,t,r){var i=this,a=!!e.bAnd,n=e.aFilters,l,u,s=a;for(var o=0;o<n.length;o++){l=n[o];u=i._evaluateFilter(l,t,r);if(a){if(!u){s=false;break}}else if(u){s=true;break}}return s};return a});