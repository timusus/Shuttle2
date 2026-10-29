import Foundation

/// The `Localizable.strings` entry [key] (an Android resource name) formatted with [args] (`%1$@`; pass an Int32 for `%1$d`).
func localizedString(_ key: String, _ args: CVarArg...) -> String {
    let format = Bundle.main.localizedString(forKey: key, value: nil, table: nil)
    return args.isEmpty ? format : String(format: format, locale: .current, arguments: args)
}
