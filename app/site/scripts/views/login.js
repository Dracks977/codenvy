/*
 * CODENVY CONFIDENTIAL
 * __________________
 * 
 *  [2012] - [2013] Codenvy, S.A. 
 *  All Rights Reserved.
 * 
 * NOTICE:  All information contained herein is, and remains
 * the property of Codenvy S.A. and its suppliers,
 * if any.  The intellectual and technical concepts contained
 * herein are proprietary to Codenvy S.A.
 * and its suppliers and may be covered by U.S. and Foreign Patents,
 * patents in process, and are protected by trade secret or copyright law.
 * Dissemination of this information or reproduction of this material
 * is strictly forbidden unless prior written permission is obtained
 * from Codenvy S.A..
 */
 
(function(window){
	define (["jquery", "underscore","backbone", "models/account", "handlebars", "text!templates/login.html", "text!templates/create.html", "text!templates/oauthbtn.html"],
	function($,_,Backbone,Account, Handlebars, loginTemplate, createTemplate, oauthTemplate){
        var action,
        errorContainer,
		oauthProviderButtons;
	    var LoginForm = Backbone.View.extend({
			loginTemplate : Handlebars.compile(loginTemplate),
			createTemplate : Handlebars.compile(createTemplate),
			oauthTemplate : Handlebars.compile(oauthTemplate),
	        initialize : function(){
                $.validator.addMethod("validDomain", function(value) {
                    return Account.isValidDomain(value.toLowerCase());
                });
                $.validator.addMethod("checkEmail", function(value) {
                    return Account.isValidEmail(value);
                });
		        oauthProviderButtons = ["google", "github"]; // the list of oauth provider buttons
                Account.isApiAvailable()
                .then(function(apiAvailable){
                    if (!apiAvailable){
                        window.location = "/site/maintenance";
                    }
                });
                $(this.el).on('submit', function(e){
                    e.preventDefault();
                });
	            if (!Account.isLoginCookiePresent() && window.location.pathname.indexOf('site/login')<0){
	                this.proceedCreate();
	            }else{
	                this.proceedLogin();
	            }
	        },
	        
	        proceedLogin : function(){
	        	action = 'login';
				var self = this;
				Account.isUserAuthenticated()
				.then(function(athenticated){
				   if (athenticated){
				       Account.navigateToLocation();
				   }else {
				       return $.Deferred().reject();
				   }
				   })
				.fail(function(){
					$(".col-md-12").append(self.loginTemplate());//TODO show Login form
				    // remove cookie to be able to sign up
					$("#signUp").click(function(){
				       $(".col-md-12").empty();//TODO hide login form
				       self.proceedCreate();
				   });
                    Account.getOAuthproviders(_.bind(self.constructOAuthElements,self));
                    self.el = $(".login-form");
                    errorContainer = $(".error-container");
                    self.addValidator();
				});
	        },

	        proceedCreate : function(){
	        	action = 'create';
	        	var self = this;
	        	$(".col-md-12").append(this.createTemplate());//TODO show Create form
                //bind onclick to Google and GitHub buttons
                $("#signIn").click(function(){
                	$(".col-md-12").empty();//TODO hide create form
                	self.proceedLogin();
                });
                Account.getOAuthproviders(_.bind(this.constructOAuthElements,this));
	        	self.el = $(".create-form");
                errorContainer = $(".error-container");
	        	self.addValidator();
	        },
            
            addValidator : function(){
                this.validator = $(this.el).validate({
                    rules: this.__validationRules(),
                    messages: this.__validationMessages(),
                    onfocusout : false, onkeyup : false,
                    submitHandler: _.bind(this.__submit,this),
                    showErrors : _.bind(function(errorMap, errorList){
                        this.__showErrors(errorMap, errorList);
                    },this)
                });    
            },
            
	        constructOAuthElements : function(deffer){
	            var self = this;
	            deffer
	            .then(function(providers){
	                _.each(providers,function(provider){
	                    if (oauthProviderButtons.indexOf(provider.name) >= 0){
	                        self.$(".oauth-list").append(
	                            self.oauthTemplate(provider)
	                        );
	                        // bind action to oauth button
	                        $(".oauth-button." + provider.name).click(function(){
	                            Account.loginWithOauthProvider(provider, "Login page", function(url){
	                                window.location = url;
	                            });
	                        });
	                    }
	                },this);
	            });
	        },

            __validationRules : function(){
                    return {
                        username: {
                            required : true,
                            email: true,
                            checkEmail : true
                        },
                        email: {
                            required: true,
                            checkEmail : true,
                            email: true
                        },
                        domain: {
                            required : true,
                            validDomain : true
                        },
                        password: {
                            required : true
                        }
                    };
                    
            },

            __validationMessages : function(){
                return {
                    username: {
                        required : this.settings.noUsernameErrorMessage
                    },
                    domain: {
                        required : this.settings.noDomainErrorMessage,
                        validDomain : this.settings.invalidDomainNameErrorMessage
                    },
                    email: {
                        required : this.settings.noEmailErrorMessage,
                        checkEmail : this.settings.invalidEmailErrorMessage
                    },
                    password: {
                        required: this.settings.noPasswordErrorMessage,
                        isValidPassword:this.settings.notSecuredPassword
                    }
                };
            },

            __showErrors : function(errorMap){
                function refocus(el){
                    el.focus();
                }
                /*this.validator.defaultShowErrors();*/

                if(typeof errorMap.email !== 'undefined'){
                    this.trigger("invalid","email",errorMap.email, errorContainer);
                    refocus(this.$("input[name='email']"));
                    return;
                }

                if(typeof errorMap.username !== 'undefined'){
                    this.trigger("invalid","username",errorMap.username, errorContainer);
                    refocus(this.$("input[name='username']"));
                    return;
                }
                
                if(typeof errorMap.domain !== 'undefined'){
                    this.trigger("invalid","domain",errorMap.domain, errorContainer);
                    refocus(this.$("input[name='domain']"));
                    return;
                }
                
                if(typeof errorMap.password !== 'undefined'){
                    this.trigger("invalid","password",errorMap.password, errorContainer);
                    refocus(this.$("input[name='password']"));
                    return;
                }


            },

            __restoreForm : function(){
                this.$("input[type='submit']").removeAttr("disabled");
            },

            __showProgress : function(){
                this.$("input[type='submit']").attr("disabled","disabled");
            },
            
            settings : {
                noDomainErrorMessage : "Please specify a workspace name",
                noUsernameErrorMessage : "We have not detected a valid user name",
                noEmailErrorMessage : "Please provide an email address",
                noPasswordErrorMessage : "Please provide your password",
                noConfirmPasswordErrorMessage : "Please type your new password again. Both passwords must match",
                invalidEmailErrorMessage : "Emails with '+' and '/' are not allowed",
                invalidDomainNameErrorMessage : "Your workspace name should start with a Latin letter or a digit, and must only contain Latin letters, digits, underscores, dots or dashes. You are allowed to use from 3 to 20 characters in a workspace name",
                notSecuredPassword : "Password should contain between 8-100 characters, both letters and digits"
            },

            __submit : function(form){
                if (Account.isWebsocketEnabled()) {
	                this.trigger("submitting");
	                if (action === 'login'){
	                    Account.processLogin(
	                            $(this.el).find("input[name='username']").val(),
	                            $(this.el).find("input[name='password']").val(),
	                            Account.getQueryParameterByName('redirect_url'),
	                            _.bind(function(errors){
	                                if(errors.length !== 0){
	                                    $(this.el).find("input[name='password']").val("");
	                                    $(this.el).find("input[name='password']").focus();
	                                    this.trigger(
	                                        "invalid",
	                                        errors[0].getFieldName(),
	                                        errors[0].getErrorDescription(),
	                                        errorContainer
	                                    );
	                                }
	                            },this)
	                        );
	                }else{
		                Account.createTenant(
		                    $(form).find("input[name='email']").val(),
		                    $(form).find("input[name='domain']").val(),
		                    _.bind(function(errors){

		                        this.__restoreForm();

		                        if(errors.length !== 0){
		                            this.trigger(
		                                "invalid",
		                                errors[0].getFieldName(),
		                                errors[0].getErrorDescription(),
		                                errorContainer
		                            );
		                        }
		                    },this)
		                );
	                }
                }
                return false;
            }
	     });
	     
	     return {
	         get : function(form){
	             if (typeof form === 'undefined'){
	                 throw new Error('Need a form');
	             }
	             return new LoginForm({
	                 el : form
	             });
	             
	         }
	     };
	 }
	 );
	}(window));