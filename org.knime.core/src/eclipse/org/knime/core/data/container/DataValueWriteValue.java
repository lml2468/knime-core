
package org.knime.core.data.container;

import org.knime.core.data.DataValue;

/**
 * TODO
 *
 * @author Christian Dietz
 */
public interface DataValueWriteValue<D extends DataValue> extends NullableWriteValue {

	void setDataValue(D value);
}
