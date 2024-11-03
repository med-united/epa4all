/*!
 * SAP SE
 * (c) Copyright 2009-2022 SAP SE or an SAP affiliate company.
 * Licensed under the Apache License, Version 2.0 - see LICENSE.txt.
 */
sap.ui.define(["sap/fhir/model/r4/type/Uri","sap/fhir/model/r4/FHIRUtils"],function(t,r){"use strict";var e=t.extend("sap.fhir.model.r4.type.Uri",{constructor:function(r,e){t.apply(this,arguments)}});e.prototype.getName=function(){return"sap.fhir.model.r4.type.Uuid"};e.prototype.toString=function(){return"uuid"};e.prototype.validateValue=function(t){return r.checkRegularExpression(t,/^[0-9A-F]{8}-[0-9A-F]{4}-4[0-9A-F]{3}-[89AB][0-9A-F]{3}-[0-9A-F]{12}$/i)};return e});