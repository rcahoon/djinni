// AUTOGENERATED FILE - DO NOT MODIFY!
// This file was generated by Djinni from exception.djinni

#pragma once

#include "djinni_wasm.hpp"
#include "js_exception.hpp"

namespace djinni_generated {

struct NativeJsException : ::djinni::JsInterface<::testsuite::JsException, NativeJsException> {
    using CppType = std::shared_ptr<::testsuite::JsException>;
    using CppOptType = std::shared_ptr<::testsuite::JsException>;
    using JsType = em::val;
    using Boxed = NativeJsException;

    static CppType toCpp(JsType j) { return _fromJs(j); }
    static JsType fromCppOpt(const CppOptType& c) { return {_toJs(c)}; }
    static JsType fromCpp(const CppType& c) {
        djinni::checkForNull(c.get(), "NativeJsException::fromCpp");
        return fromCppOpt(c);
    }


    struct JsProxy: ::djinni::JsProxyBase, ::testsuite::JsException, ::djinni::InstanceTracker<JsProxy> {
        JsProxy(const em::val& v) : JsProxyBase(v) {}
        void throw_js_exception() override;
    };
};

}  // namespace djinni_generated
