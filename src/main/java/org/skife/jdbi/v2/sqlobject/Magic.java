package org.skife.jdbi.v2.sqlobject;

import com.fasterxml.classmate.ResolvedType;
import com.fasterxml.classmate.members.ResolvedMethod;
import org.skife.jdbi.v2.Query;
import org.skife.jdbi.v2.ResultBearing;
import org.skife.jdbi.v2.ResultIterator;
import org.skife.jdbi.v2.exceptions.UnableToCreateStatementException;
import org.skife.jdbi.v2.sqlobject.customizers.Mapper;
import org.skife.jdbi.v2.tweak.ResultSetMapper;

import java.util.Iterator;
import java.util.List;

abstract class Magic
{
    public Object map(ResolvedMethod method, Query q, HandleDing h)
    {
        if (method.getRawMember().isAnnotationPresent(Mapper.class)) {
            final ResultSetMapper mapper;
            try {
                mapper = method.getRawMember().getAnnotation(Mapper.class).value().newInstance();
            }
            catch (Exception e) {
                throw new UnableToCreateStatementException("unable to access mapper", e);
            }
            return result(q.map(mapper), h);
        }
        else {
            return result(q.mapTo(mapTo(method).getErasedType()), h);
        }
    }

    static Magic forType(ResolvedMethod method)
    {
        ResolvedType return_type = method.getReturnType();
        if (return_type.isInstanceOf(ResultBearing.class)) {
            return new ResultBearingMagic(method);
        }
        else if (return_type.isInstanceOf(List.class)) {
            return new Magic.ListMagic(method);
        }
        else if (return_type.isInstanceOf(Iterator.class)) {
            return new Magic.IteratorMagic(method);
        }
        else {
            return new Magic.SingleValueMagic(method);
        }
    }

    protected abstract Object result(ResultBearing q, HandleDing baton);

    protected abstract ResolvedType mapTo(ResolvedMethod method);


    static class SingleValueMagic extends Magic
    {
        private final ResolvedType returnType;

        public SingleValueMagic(ResolvedMethod method)
        {
            this.returnType = method.getReturnType();
        }

        @Override
        protected Object result(ResultBearing q, HandleDing baton)
        {
            return q.first();
        }

        @Override
        protected ResolvedType mapTo(ResolvedMethod method)
        {
            return returnType;
        }
    }

    static class ResultBearingMagic extends Magic
    {

        private final ResolvedType resolvedType;

        public ResultBearingMagic(ResolvedMethod method)
        {
            // extract T from Query<T>
            ResolvedType query_type = method.getReturnType();
            List<ResolvedType> query_return_types = query_type.typeParametersFor(org.skife.jdbi.v2.Query.class);
            this.resolvedType = query_return_types.get(0);

        }

        @Override
        protected Object result(ResultBearing q, HandleDing baton)
        {
            return q;
        }

        @Override
        protected ResolvedType mapTo(ResolvedMethod method)
        {
            return resolvedType;
        }
    }

    static class IteratorMagic extends Magic
    {
        private final ResolvedType resolvedType;

        public IteratorMagic(ResolvedMethod method)
        {
            ResolvedType query_type = method.getReturnType();
            List<ResolvedType> query_return_types = query_type.typeParametersFor(Iterator.class);
            this.resolvedType = query_return_types.get(0);

        }

        @Override
        protected Object result(ResultBearing q, final HandleDing baton)
        {
            baton.retain("iterator");
            final ResultIterator itty = q.iterator();

            return new ResultIterator()
            {
                public void close()
                {
                    itty.close();
                }

                public boolean hasNext()
                {
                    boolean has_next = itty.hasNext();
                    if (!has_next) {
                        baton.release("iterator");
                    }
                    return itty.hasNext();
                }

                public Object next()
                {
                    Object rs = itty.next();
                    boolean has_next = itty.hasNext();
                    if (!has_next) {
                        baton.release("iterator");
                    }
                    return rs;
                }

                public void remove()
                {
                    itty.remove();
                }
            };
        }

        @Override
        protected ResolvedType mapTo(ResolvedMethod method)
        {
            return resolvedType;
        }
    }

    static class ListMagic extends Magic
    {
        private final ResolvedType resolvedType;

        public ListMagic(ResolvedMethod method)
        {
            // extract T from List<T>
            ResolvedType query_type = method.getReturnType();
            List<ResolvedType> query_return_types = query_type.typeParametersFor(List.class);
            this.resolvedType = query_return_types.get(0);
        }

        @Override
        protected Object result(ResultBearing q, HandleDing baton)
        {
            return q.list();
        }

        @Override
        protected ResolvedType mapTo(ResolvedMethod method)
        {
            return resolvedType;
        }
    }
}
