import SwiftUI
import ViewInspector

extension InspectableView {
    /// Every `V` in the hierarchy, as `findAll` would return them, but found breadth first, so views inside a
    /// value-based `NavigationLink`'s label are reached. ViewInspector 0.10.3's `findAll` gives up on the whole
    /// link: it gathers a view's children, label and modifiers in one throwing step, and a value-based link's
    /// destination lookup throws, where `find` searches the three separately. On iOS 26 `LibraryRowLink` wraps
    /// each Library row in such a link (#681, #686). Views at one depth come back in order, as list rows are.
    func findAllBreadthFirst<V: SwiftUI.View>(_ type: V.Type) -> [InspectableView<ViewType.View<V>>] {
        var found: [InspectableView<ViewType.View<V>>] = []
        // A condition that never matches visits every view; the search then throws, having found none.
        _ = try? find(type, where: { view in
            found.append(view)
            return false
        })
        return found
    }
}
